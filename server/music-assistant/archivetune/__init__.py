"""ArchiveTune provider for Music Assistant.

Every request goes to an ArchiveTune Server you host. Music Assistant never
talks to YouTube itself, so no yt-dlp or ytmusicapi is needed in its container.
"""

from __future__ import annotations

__version__ = "0.1.0"

import asyncio
import re
import unicodedata
from collections import OrderedDict
from typing import TYPE_CHECKING, Any
from urllib.parse import quote, urlencode

import aiohttp
from music_assistant_models.config_entries import ConfigEntry, ConfigValueType
from music_assistant_models.enums import (
    AlbumType,
    ConfigEntryType,
    ContentType,
    ImageType,
    MediaType,
    ProviderFeature,
    StreamType,
)
from music_assistant_models.errors import (
    InvalidDataError,
    LoginFailed,
    MediaNotFoundError,
    ProviderUnavailableError,
    SetupFailedError,
)
from music_assistant_models.media_items import (
    Album,
    Artist,
    AudioFormat,
    ItemMapping,
    MediaItemImage,
    ProviderMapping,
    SearchResults,
    Track,
    UniqueList,
)
from music_assistant_models.streamdetails import StreamDetails

from music_assistant.helpers.util import infer_album_type, parse_title_and_version
from music_assistant.models.music_provider import MusicProvider

if TYPE_CHECKING:
    from music_assistant_models.config_entries import ProviderConfig
    from music_assistant_models.provider import ProviderManifest

    from music_assistant import MusicAssistant
    from music_assistant.models import ProviderInstanceType

CONF_URL = "server_url"
CONF_TOKEN = "token"

SUPPORTED_FEATURES = {
    ProviderFeature.SEARCH,
    ProviderFeature.ARTIST_ALBUMS,
    ProviderFeature.ARTIST_TOPTRACKS,
    ProviderFeature.SIMILAR_TRACKS,
}

NAME_ID_PREFIX = "name:"
REQUEST_TIMEOUT = 45
HEALTH_TIMEOUT = 10
TRACK_CACHE_SIZE = 512
ARTIST_ID_CACHE_SIZE = 256
DEFAULT_STREAM_EXPIRATION = 3600


async def setup(
    mass: MusicAssistant, manifest: ProviderManifest, config: ProviderConfig
) -> ProviderInstanceType:
    """Initialize provider(instance) with given configuration."""
    return ArchiveTuneProvider(mass, manifest, config, SUPPORTED_FEATURES)


async def get_config_entries(
    mass: MusicAssistant,  # noqa: ARG001
    instance_id: str | None = None,  # noqa: ARG001
    action: str | None = None,  # noqa: ARG001
    values: dict[str, ConfigValueType] | None = None,  # noqa: ARG001
) -> tuple[ConfigEntry, ...]:
    """Return config entries for Music Assistant 2.9.x."""
    return _build_config_entries()


def _build_config_entries() -> tuple[ConfigEntry, ...]:
    return (
        ConfigEntry(
            key=CONF_URL,
            type=ConfigEntryType.STRING,
            label="ArchiveTune Server URL",
            default_value="http://homeassistant.local:8780",
            required=True,
            description="Address of your ArchiveTune Server, reachable from Music Assistant "
            "(for example http://192.168.1.50:8780).",
        ),
        ConfigEntry(
            key=CONF_TOKEN,
            type=ConfigEntryType.SECURE_STRING,
            label="Token",
            default_value="",
            required=False,
            description="Value of ARCHIVETUNE_TOKEN on the server. Leave empty if the server "
            "runs without a token.",
        ),
    )


def normalize_server_url(raw: str) -> str:
    """Return the server base URL without trailing slash, adding http:// when missing."""
    url = (raw or "").strip().rstrip("/")
    if not url:
        raise SetupFailedError("ArchiveTune Server URL is empty")
    if "://" not in url:
        url = f"http://{url}"
    return url


def normalize_name(value: str) -> str:
    """Lowercase, strip accents and punctuation for name comparisons."""
    decomposed = unicodedata.normalize("NFKD", value or "")
    stripped = "".join(c for c in decomposed if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", stripped.lower()).strip()


def content_type_from_mime(mime_type: str) -> ContentType:
    """Map a server mimeType such as audio/webm; codecs="opus" to a ContentType."""
    lowered = (mime_type or "").lower()
    if "opus" in lowered:
        return ContentType.OPUS
    if "mp4a" in lowered or "mp4" in lowered or "m4a" in lowered or "aac" in lowered:
        return ContentType.M4A
    if "vorbis" in lowered or "ogg" in lowered:
        return ContentType.OGG
    if "mpeg" in lowered or "mp3" in lowered:
        return ContentType.MP3
    if "flac" in lowered:
        return ContentType.FLAC
    return ContentType.UNKNOWN


class ArchiveTuneProvider(MusicProvider):
    """Music Assistant provider backed by an ArchiveTune Server."""

    _base_url: str = ""
    _token: str = ""
    _ready: bool = False

    async def get_config_entries(self) -> tuple[ConfigEntry, ...]:  # type: ignore[override]
        """Return config entries for Music Assistant 2.10."""
        return _build_config_entries()

    async def handle_async_init(self) -> None:
        """Validate the configuration and reach the server."""
        self.logger.info("ArchiveTune provider version %s", __version__)
        self._base_url = normalize_server_url(str(self.config.get_value(CONF_URL) or ""))
        self._token = str(self.config.get_value(CONF_TOKEN) or "").strip()
        self._tracks: OrderedDict[str, dict[str, Any]] = OrderedDict()
        self._artist_ids: OrderedDict[str, str] = OrderedDict()
        health = await self._request_json("/api/health", authorized=False, timeout=HEALTH_TIMEOUT)
        if not isinstance(health, dict) or health.get("status") != "ok":
            raise SetupFailedError(f"{self._base_url} is not an ArchiveTune Server")
        self.logger.info("Connected to ArchiveTune Server %s", health.get("version", "unknown"))
        await self._request_json("/api/devices", timeout=HEALTH_TIMEOUT)
        self._ready = True

    @property
    def is_streaming_provider(self) -> bool:
        """Content comes from a streaming catalogue, not a local library."""
        return True

    def _headers(self, authorized: bool = True) -> dict[str, str]:
        if authorized and self._token:
            return {"Authorization": f"Bearer {self._token}"}
        return {}

    async def _request_json(
        self,
        path: str,
        params: dict[str, Any] | None = None,
        authorized: bool = True,
        timeout: float = REQUEST_TIMEOUT,
    ) -> Any:
        url = f"{self._base_url}{path}"
        try:
            async with self.mass.http_session.get(
                url,
                params=params,
                headers=self._headers(authorized),
                timeout=aiohttp.ClientTimeout(total=timeout),
            ) as response:
                if response.status == 401:
                    raise LoginFailed("ArchiveTune Server refused the token")
                if response.status == 400:
                    raise MediaNotFoundError(await self._error_text(response))
                if response.status >= 400:
                    raise InvalidDataError(
                        f"ArchiveTune Server answered {response.status}: "
                        f"{await self._error_text(response)}"
                    )
                return await response.json(content_type=None)
        except (aiohttp.ClientError, asyncio.TimeoutError) as err:
            message = f"Cannot reach ArchiveTune Server at {self._base_url}: {err}"
            if self._ready:
                raise ProviderUnavailableError(message) from err
            raise SetupFailedError(message) from err

    @staticmethod
    async def _error_text(response: aiohttp.ClientResponse) -> str:
        try:
            body = await response.json(content_type=None)
        except (aiohttp.ClientError, ValueError):
            return response.reason or "error"
        if isinstance(body, dict) and body.get("error"):
            return str(body["error"])
        return response.reason or "error"

    def _remember_track(self, data: dict[str, Any]) -> None:
        track_id = data.get("id")
        if not track_id:
            return
        self._tracks[track_id] = data
        self._tracks.move_to_end(track_id)
        while len(self._tracks) > TRACK_CACHE_SIZE:
            self._tracks.popitem(last=False)

    def _remember_artist_id(self, name: str, artist_id: str) -> None:
        key = normalize_name(name)
        if not key:
            return
        self._artist_ids[key] = artist_id
        self._artist_ids.move_to_end(key)
        while len(self._artist_ids) > ARTIST_ID_CACHE_SIZE:
            self._artist_ids.popitem(last=False)

    def _images(self, thumbnail: str | None) -> UniqueList[MediaItemImage]:
        if not thumbnail:
            return UniqueList()
        return UniqueList(
            [
                MediaItemImage(
                    type=ImageType.THUMB,
                    path=thumbnail,
                    provider=self.instance_id,
                    remotely_accessible=True,
                )
            ]
        )

    def _provider_mapping(self, item_id: str, available: bool = True) -> ProviderMapping:
        return ProviderMapping(
            item_id=item_id,
            provider_domain=self.domain,
            provider_instance=self.instance_id,
            available=available,
        )

    def _artist_mapping(self, name: str, artist_id: str | None = None) -> ItemMapping:
        item_id = artist_id or f"{NAME_ID_PREFIX}{name}"
        return ItemMapping(
            media_type=MediaType.ARTIST,
            item_id=item_id,
            provider=self.instance_id,
            name=name,
        )

    def _parse_artist(self, data: dict[str, Any]) -> Artist:
        artist_id = data.get("id")
        if not artist_id:
            raise InvalidDataError("Artist is missing an id")
        name = data.get("name") or "Unknown Artist"
        self._remember_artist_id(name, artist_id)
        artist = Artist(
            item_id=artist_id,
            provider=self.instance_id,
            name=name,
            provider_mappings={self._provider_mapping(artist_id)},
        )
        artist.metadata.images = self._images(data.get("thumbnail"))
        return artist

    def _parse_album(self, data: dict[str, Any]) -> Album:
        album_id = data.get("id")
        if not album_id:
            raise InvalidDataError("Album is missing an id")
        name, version = parse_title_and_version(data.get("title") or "Unknown")
        album = Album(
            item_id=album_id,
            provider=self.instance_id,
            name=name,
            version=version,
            provider_mappings={self._provider_mapping(album_id)},
        )
        year = data.get("year")
        if isinstance(year, int) and year > 0:
            album.year = year
        album.metadata.images = self._images(data.get("thumbnail"))
        album.metadata.explicit = bool(data.get("explicit"))
        names = [str(a) for a in data.get("artists") or [] if a]
        album.artists = UniqueList([self._artist_mapping(a) for a in names])
        album.album_type = infer_album_type(name, version)
        if album.album_type == AlbumType.UNKNOWN:
            album.album_type = AlbumType.ALBUM
        return album

    def _parse_track(self, data: dict[str, Any], track_number: int = 0) -> Track:
        track_id = data.get("id")
        if not track_id:
            raise InvalidDataError("Track is missing an id")
        self._remember_track(data)
        name, version = parse_title_and_version(data.get("title") or "Unknown")
        track = Track(
            item_id=track_id,
            provider=self.instance_id,
            name=name,
            version=version,
            provider_mappings={self._provider_mapping(track_id)},
            disc_number=0,
            track_number=track_number,
        )
        names = [str(a) for a in data.get("artists") or [] if a]
        track.artists = UniqueList([self._artist_mapping(a) for a in names]) if names else UniqueList(
            [self._artist_mapping("Unknown Artist")]
        )
        album_id = data.get("albumId")
        if album_id and data.get("album"):
            track.album = ItemMapping(
                media_type=MediaType.ALBUM,
                item_id=album_id,
                provider=self.instance_id,
                name=data["album"],
            )
        duration = data.get("durationSeconds")
        if isinstance(duration, int) and duration > 0:
            track.duration = duration
        track.metadata.images = self._images(data.get("thumbnail"))
        track.metadata.explicit = bool(data.get("explicit"))
        return track

    async def search(
        self,
        search_query: str,
        media_types: list[MediaType],
        limit: int = 5,
    ) -> SearchResults:
        """Search tracks, albums and artists on the server."""
        wanted = {MediaType.TRACK, MediaType.ALBUM, MediaType.ARTIST} & set(media_types)
        if not wanted or not search_query.strip():
            return SearchResults()
        search_type = "all"
        if len(wanted) == 1:
            search_type = {
                MediaType.TRACK: "song",
                MediaType.ALBUM: "album",
                MediaType.ARTIST: "artist",
            }[next(iter(wanted))]
        body = await self._request_json("/api/search", {"q": search_query, "type": search_type})
        results = SearchResults()
        if MediaType.TRACK in wanted:
            results.tracks = self._parse_list(body.get("tracks"), self._parse_track, limit)
        if MediaType.ALBUM in wanted:
            results.albums = self._parse_list(body.get("albums"), self._parse_album, limit)
        if MediaType.ARTIST in wanted:
            results.artists = self._parse_list(body.get("artists"), self._parse_artist, limit)
        return results

    def _parse_list(self, items: Any, parser: Any, limit: int) -> list[Any]:
        parsed: list[Any] = []
        for item in items or []:
            try:
                parsed.append(parser(item))
            except InvalidDataError:
                continue
            if len(parsed) >= limit:
                break
        return parsed

    async def get_track(self, prov_track_id: str) -> Track:
        """Get full track details by id."""
        cached = self._tracks.get(prov_track_id)
        if cached is None:
            related = await self._request_json(f"/api/radio/{quote(prov_track_id, safe='')}")
            for item in related or []:
                self._remember_track(item)
            cached = self._tracks.get(prov_track_id)
        if cached is None:
            raise MediaNotFoundError(f"Track {prov_track_id} not found")
        return self._parse_track(cached)

    async def get_album(self, prov_album_id: str) -> Album:
        """Get full album details by id."""
        details = await self._request_json(f"/api/album/{quote(prov_album_id, safe='')}")
        return self._parse_album({**details["album"], "id": prov_album_id})

    async def get_album_tracks(self, prov_album_id: str) -> list[Track]:
        """Get album tracks for given album id."""
        details = await self._request_json(f"/api/album/{quote(prov_album_id, safe='')}")
        album = details["album"]
        tracks: list[Track] = []
        for position, item in enumerate(details.get("tracks") or [], start=1):
            enriched = {
                **item,
                "albumId": item.get("albumId") or prov_album_id,
                "album": item.get("album") or album.get("title"),
            }
            try:
                tracks.append(self._parse_track(enriched, track_number=position))
            except InvalidDataError:
                continue
        return tracks

    async def _resolve_artist_id(self, prov_artist_id: str) -> str:
        if not prov_artist_id.startswith(NAME_ID_PREFIX):
            return prov_artist_id
        name = prov_artist_id[len(NAME_ID_PREFIX) :]
        key = normalize_name(name)
        if key in self._artist_ids:
            return self._artist_ids[key]
        body = await self._request_json("/api/search", {"q": name, "type": "artist"})
        candidates = body.get("artists") or []
        if not candidates:
            raise MediaNotFoundError(f"Artist {name} not found")
        exact = [c for c in candidates if normalize_name(c.get("name", "")) == key]
        chosen = (exact or candidates)[0]
        self._remember_artist_id(name, chosen["id"])
        return chosen["id"]

    async def _artist_details(self, prov_artist_id: str) -> dict[str, Any]:
        real_id = await self._resolve_artist_id(prov_artist_id)
        return await self._request_json(f"/api/artist/{quote(real_id, safe='')}")

    async def get_artist(self, prov_artist_id: str) -> Artist:
        """Get full artist details by id."""
        details = await self._artist_details(prov_artist_id)
        artist = self._parse_artist({**details["artist"], "id": details["artist"]["id"]})
        if prov_artist_id != artist.item_id:
            artist.item_id = prov_artist_id
            artist.provider_mappings = {self._provider_mapping(prov_artist_id)}
        if details.get("description"):
            artist.metadata.description = details["description"]
        return artist

    async def get_artist_albums(self, prov_artist_id: str) -> list[Album]:
        """Get albums, EPs and singles for the given artist."""
        details = await self._artist_details(prov_artist_id)
        raw = [*(details.get("albums") or []), *(details.get("singles") or [])]
        return self._parse_list(raw, self._parse_album, len(raw) or 1)

    async def get_artist_toptracks(self, prov_artist_id: str) -> list[Track]:
        """Get the most popular tracks for the given artist."""
        details = await self._artist_details(prov_artist_id)
        raw = details.get("topTracks") or []
        return self._parse_list(raw, self._parse_track, len(raw) or 1)

    async def get_similar_tracks(self, prov_track_id: str, limit: int = 25) -> list[Track]:
        """Return the server's radio for a track, without the seed itself."""
        related = await self._request_json(f"/api/radio/{quote(prov_track_id, safe='')}")
        others = [item for item in related or [] if item.get("id") != prov_track_id]
        return self._parse_list(others, self._parse_track, limit)

    def _stream_url(self, item_id: str) -> str:
        url = f"{self._base_url}/stream/{quote(item_id, safe='')}"
        if self._token:
            return f"{url}?{urlencode({'token': self._token})}"
        return url

    async def get_stream_details(self, item_id: str, media_type: MediaType) -> StreamDetails:
        """Return an HTTP stream served by the ArchiveTune Server."""
        info = await self._request_json("/api/resolve", {"id": item_id})
        duration_ms = info.get("durationMs")
        audio_format = AudioFormat(
            content_type=content_type_from_mime(str(info.get("mimeType", ""))),
            bit_rate=int(info["bitrate"]) if info.get("bitrate") else None,
        )
        return StreamDetails(
            provider=self.instance_id,
            item_id=item_id,
            audio_format=audio_format,
            media_type=media_type,
            stream_type=StreamType.HTTP,
            path=self._stream_url(item_id),
            duration=int(duration_ms / 1000) if duration_ms else None,
            size=info.get("contentLength"),
            can_seek=True,
            allow_seek=True,
            expiration=DEFAULT_STREAM_EXPIRATION,
        )

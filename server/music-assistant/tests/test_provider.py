import json
from pathlib import Path

import pytest
from music_assistant_models.enums import AlbumType, ContentType, MediaType, StreamType
from music_assistant_models.errors import (
    LoginFailed,
    MediaNotFoundError,
    ProviderUnavailableError,
    SetupFailedError,
)

MANIFEST = Path(__file__).resolve().parents[1] / "archivetune" / "manifest.json"


def test_manifest_matches_module(module):
    manifest = json.loads(MANIFEST.read_text())
    assert manifest["domain"] == "archivetune"
    assert manifest["type"] == "music"
    assert manifest["requirements"] == []
    assert manifest["multi_instance"] is True


def test_config_entries_cover_url_and_token(module):
    entries = module._build_config_entries()
    keys = {entry.key: entry for entry in entries}
    assert set(keys) == {module.CONF_URL, module.CONF_TOKEN}
    assert keys[module.CONF_URL].required is True
    assert keys[module.CONF_TOKEN].required is False


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("http://192.168.1.5:8780/", "http://192.168.1.5:8780"),
        ("192.168.1.5:8780", "http://192.168.1.5:8780"),
        ("  https://nas.local:8780// ", "https://nas.local:8780"),
    ],
)
def test_normalize_server_url(module, raw, expected):
    assert module.normalize_server_url(raw) == expected


def test_normalize_server_url_rejects_empty(module):
    with pytest.raises(SetupFailedError):
        module.normalize_server_url("  ")


@pytest.mark.parametrize(
    ("mime", "expected"),
    [
        ('audio/webm; codecs="opus"', ContentType.OPUS),
        ('audio/mp4; codecs="mp4a.40.2"', ContentType.M4A),
        ("audio/mpeg", ContentType.MP3),
        ("", ContentType.UNKNOWN),
    ],
)
def test_content_type_from_mime(module, mime, expected):
    assert module.content_type_from_mime(mime) == expected


def test_normalize_name_strips_accents_and_case(module):
    assert module.normalize_name("  Céline  DION!") == "celine dion"


async def test_init_checks_health_and_token(server_factory, make_provider):
    server = await server_factory(token="secret")
    provider = await make_provider(server, token="secret")
    paths = [path for path, _ in server.calls]
    assert "/api/health" in paths
    assert "/api/devices" in paths
    assert provider._ready is True


async def test_init_rejects_wrong_token(server_factory, make_provider):
    server = await server_factory(token="secret")
    with pytest.raises(LoginFailed):
        await make_provider(server, token="nope")


async def test_init_rejects_unreachable_server(server_factory, make_provider):
    server = await server_factory()
    with pytest.raises(SetupFailedError):
        await make_provider(server, url="http://127.0.0.1:1")


async def test_runtime_outage_is_provider_unavailable(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    provider._base_url = "http://127.0.0.1:1"
    with pytest.raises(ProviderUnavailableError):
        await provider.get_album_tracks("MPREb_disc")


async def test_search_all_types(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    results = await provider.search(
        "daft punk", [MediaType.TRACK, MediaType.ALBUM, MediaType.ARTIST], limit=5
    )
    assert [t.item_id for t in results.tracks] == ["aaaaaaaaaaa", "bbbbbbbbbbb"]
    assert [a.item_id for a in results.albums] == ["MPREb_disc"]
    assert [a.item_id for a in results.artists] == ["UCdaftpunk"]
    assert server.calls[-1][1]["type"] == "all"


async def test_search_single_type_uses_specific_query(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    results = await provider.search("daft punk", [MediaType.ARTIST])
    assert [a.name for a in results.artists] == ["Daft Punk"]
    assert server.calls[-1][1]["type"] == "artist"
    assert not results.tracks


async def test_search_respects_limit_and_ignores_other_types(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    results = await provider.search("x", [MediaType.TRACK, MediaType.PLAYLIST], limit=1)
    assert len(results.tracks) == 1
    empty = await provider.search("x", [MediaType.PLAYLIST])
    assert not empty.tracks


async def test_track_fields(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    results = await provider.search("x", [MediaType.TRACK, MediaType.ALBUM, MediaType.ARTIST])
    first, second = results.tracks
    assert first.name == "Alpha"
    assert first.version == "Live"
    assert first.duration == 200
    assert first.album.item_id == "MPREb_disc"
    assert first.artists[0].item_id == "name:Daft Punk"
    assert len(first.metadata.images) == 1
    assert second.metadata.explicit is True
    assert [a.name for a in second.artists] == ["Daft Punk", "Pharrell"]
    assert second.album is None
    assert not second.metadata.images


async def test_get_track_uses_cache_then_radio(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    cold = await provider.get_track("bbbbbbbbbbb")
    assert cold.name == "Beta"
    assert server.calls[-1][0] == "/api/radio/bbbbbbbbbbb"
    calls_before = len(server.calls)
    warm = await provider.get_track("bbbbbbbbbbb")
    assert warm.item_id == "bbbbbbbbbbb"
    assert len(server.calls) == calls_before


async def test_get_track_unknown(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    with pytest.raises(MediaNotFoundError):
        await provider.get_track("unknownxxxx")


async def test_album_and_tracks(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    album = await provider.get_album("MPREb_disc")
    assert album.name == "Discovery"
    assert album.year == 2001
    assert album.album_type == AlbumType.ALBUM
    assert album.artists[0].name == "Daft Punk"
    tracks = await provider.get_album_tracks("MPREb_disc")
    assert [t.track_number for t in tracks] == [1, 2]
    assert tracks[0].album.item_id == "MPREb_disc"
    assert tracks[0].album.name == "Discovery"


async def test_live_album_type(server_factory, make_provider, module):
    server = await server_factory()
    provider = await make_provider(server)
    album = provider._parse_album({"id": "MPREb_x", "title": "Alive Live", "artists": []})
    assert album.album_type == AlbumType.LIVE


async def test_artist_by_real_id(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    artist = await provider.get_artist("UCdaftpunk")
    assert artist.item_id == "UCdaftpunk"
    assert artist.metadata.description == "French house duo"
    albums = await provider.get_artist_albums("UCdaftpunk")
    assert {a.item_id for a in albums} == {"MPREb_disc", "MPREb_single"}
    top = await provider.get_artist_toptracks("UCdaftpunk")
    assert [t.item_id for t in top] == ["aaaaaaaaaaa"]


async def test_artist_by_name_fallback_resolves_through_search(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    artist = await provider.get_artist("name:Daft Punk")
    assert artist.item_id == "name:Daft Punk"
    assert artist.name == "Daft Punk"
    searches = [c for c in server.calls if c[0] == "/api/search"]
    assert searches[-1][1] == {"q": "Daft Punk", "type": "artist"}
    assert server.calls[-1][0] == "/api/artist/UCdaftpunk"
    server.calls.clear()
    await provider.get_artist_albums("name:Daft Punk")
    assert [c[0] for c in server.calls] == ["/api/artist/UCdaftpunk"]


async def test_artist_by_name_not_found(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    with pytest.raises(MediaNotFoundError):
        await provider.get_artist("name:Zzzz Nobody")


async def test_similar_tracks_exclude_seed(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    similar = await provider.get_similar_tracks("aaaaaaaaaaa", limit=10)
    assert [t.item_id for t in similar] == ["bbbbbbbbbbb"]


async def test_stream_details_with_token(server_factory, make_provider):
    server = await server_factory(token="secret")
    provider = await make_provider(server, token="secret")
    details = await provider.get_stream_details("aaaaaaaaaaa", MediaType.TRACK)
    assert details.stream_type == StreamType.HTTP
    assert details.path == f"{server.url}/stream/aaaaaaaaaaa?token=secret"
    assert details.audio_format.content_type == ContentType.OPUS
    assert details.audio_format.bit_rate == 131
    assert details.duration == 200
    assert details.can_seek is True
    assert details.allow_seek is True
    assert details.item_id == "aaaaaaaaaaa"


async def test_stream_details_without_token(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    server.stream_codec = 'audio/mp4; codecs="mp4a.40.2"'
    details = await provider.get_stream_details("aaaaaaaaaaa", MediaType.TRACK)
    assert details.path == f"{server.url}/stream/aaaaaaaaaaa"
    assert details.audio_format.content_type == ContentType.M4A


async def test_stream_details_failure_surfaces(server_factory, make_provider):
    server = await server_factory()
    provider = await make_provider(server)
    server.fail_resolve = True
    with pytest.raises(Exception, match="no playable format"):
        await provider.get_stream_details("aaaaaaaaaaa", MediaType.TRACK)


async def test_token_header_sent_on_api_calls(server_factory, make_provider):
    server = await server_factory(token="secret")
    provider = await make_provider(server, token="secret")
    results = await provider.search("daft", [MediaType.TRACK])
    assert len(results.tracks) == 2


async def test_values_from_setup_flow_are_used(server_factory, make_provider):
    server = await server_factory(token="secret")
    provider = await make_provider(server, token="secret", via="setup_data")
    assert provider._base_url == server.url
    assert provider._token == "secret"


async def test_values_from_plain_config_are_used(server_factory, make_provider):
    server = await server_factory(token="secret")
    provider = await make_provider(server, token="secret", via="config")
    assert provider._base_url == server.url
    assert provider._token == "secret"


async def test_empty_setup_data_fails_with_clear_message(server_factory, make_provider):
    server = await server_factory()
    with pytest.raises(SetupFailedError, match="URL is empty"):
        await make_provider(server, url=" ", via="setup_data")


async def test_old_server_without_devices_endpoint_still_starts(server_factory, make_provider):
    server = await server_factory()
    server.no_devices = True
    provider = await make_provider(server)
    assert provider._ready is True

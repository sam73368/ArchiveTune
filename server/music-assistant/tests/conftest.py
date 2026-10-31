import asyncio
import logging
import sys
import types
from pathlib import Path

import pytest
import pytest_asyncio

PACKAGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))


def _module(name: str) -> types.ModuleType:
    module = types.ModuleType(name)
    sys.modules[name] = module
    return module


def _install_music_assistant_stub() -> None:
    from music_assistant_models.enums import AlbumType

    package = _module("music_assistant")
    package.MusicAssistant = object
    helpers = _module("music_assistant.helpers")
    util = _module("music_assistant.helpers.util")

    def infer_album_type(name: str, version: str) -> AlbumType:
        text = f"{name} {version or ''}".lower()
        if "live" in text:
            return AlbumType.LIVE
        return AlbumType.UNKNOWN

    def parse_title_and_version(title: str) -> tuple[str, str]:
        if title.endswith(")") and " (" in title:
            base, _, version = title[:-1].rpartition(" (")
            return base, version
        return title, ""

    util.infer_album_type = infer_album_type
    util.parse_title_and_version = parse_title_and_version
    helpers.util = util
    package.helpers = helpers

    models = _module("music_assistant.models")
    music_provider = _module("music_assistant.models.music_provider")

    class MusicProvider:
        instance_id = "archivetune--test"
        domain = "archivetune"

        def __init__(self, mass, manifest, config, supported_features):
            self.mass = mass
            self.manifest = manifest
            self.config = config
            self.supported_features = supported_features
            self.logger = logging.getLogger("archivetune_test")

        @property
        def is_streaming_provider(self) -> bool:
            return True

    music_provider.MusicProvider = MusicProvider
    models.music_provider = music_provider
    models.ProviderInstanceType = object
    package.models = models


_install_music_assistant_stub()

import importlib.util  # noqa: E402

_spec = importlib.util.spec_from_file_location(
    "archivetune_provider",
    PACKAGE_ROOT / "archivetune" / "__init__.py",
    submodule_search_locations=[str(PACKAGE_ROOT / "archivetune")],
)
provider_module = importlib.util.module_from_spec(_spec)
sys.modules["archivetune_provider"] = provider_module
_spec.loader.exec_module(provider_module)


class FakeConfig:
    def __init__(self, values):
        self.values = values

    def get_value(self, key):
        return self.values.get(key)


class FakeMass:
    def __init__(self, session):
        self.http_session = session


@pytest.fixture
def module():
    return provider_module


@pytest_asyncio.fixture
async def server_factory():
    servers = []

    async def factory(token=""):
        from fake_server import FakeServer

        server = FakeServer(token=token)
        await server.start()
        servers.append(server)
        return server

    yield factory
    for server in servers:
        await server.stop()


@pytest_asyncio.fixture
async def make_provider():
    sessions = []

    async def factory(server, token="", url=None, init=True):
        import aiohttp

        session = aiohttp.ClientSession()
        sessions.append(session)
        values = {
            provider_module.CONF_URL: url or server.url,
            provider_module.CONF_TOKEN: token,
        }
        provider = provider_module.ArchiveTuneProvider(
            FakeMass(session), None, FakeConfig(values), provider_module.SUPPORTED_FEATURES
        )
        if init:
            await provider.handle_async_init()
        return provider

    yield factory
    for session in sessions:
        await session.close()

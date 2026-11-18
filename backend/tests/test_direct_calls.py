from io import BytesIO
from uuid import uuid4

import pytest

from test_android_releases import release
from test_api import app, client, fake
from app import direct_calls
from app.android_releases import AndroidReleases
from app.config import settings


class Push:
    def __init__(self):
        self.notifications = []
        self.result = "sent"

    async def send(self, token, data, ttl):
        self.notifications.append((token, data))
        return self.result


@pytest.fixture
def calls(client, tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "direct_calls_proxy_secret", "selfcall-test-proxy")
    push = Push()
    app.state.direct_calls = direct_calls.DirectCalls(str(tmp_path / "calls.sqlite"), push)
    try:
        yield client, push
    finally:
        app.state.direct_calls = None


def headers(login):
    return {"X-SelfCall-Login": login, "X-SelfCall-Proxy-Secret": "selfcall-test-proxy"}


def register(client, login, fid, name="Алексей"):
    device_id = str(uuid4())
    response = client.post("/api/calls/enable", headers=headers(login), json={
        "device_id": device_id, "name": name, "token": fid,
    })
    assert response.status_code == 200
    return device_id


def test_only_authenticated_proxy_can_use_directory(calls):
    client, _ = calls
    path = f"/api/calls/directory?device_id={uuid4()}"
    assert client.get(path, headers={"X-SelfCall-Login": "alice"}).status_code == 401
    assert client.get("/api/calls/directory").status_code == 401


def test_switching_phones_keeps_only_one_incoming_registration(calls):
    client, _ = calls
    old = register(client, "bob", "a" * 22)
    current = register(client, "bob", "b" * 22)
    register(client, "anton", "c" * 22)
    path = "/api/calls/directory?device_id="
    assert client.get(path + old, headers=headers("bob")).json()["incoming"] is None
    assert client.get(path + current, headers=headers("bob")).json()["incoming"]["name"] == "Алексей"
    client.post("/api/calls/disable", headers=headers("bob"), json={"device_id": old})
    names = client.get(path + str(uuid4()), headers=headers("alice")).json()["contacts"]
    assert names == [{"login": "anton", "name": "Алексей"}, {"login": "bob", "name": "Алексей"}]
    client.post("/api/calls/disable", headers=headers("bob"), json={"device_id": current})
    names = client.get(path + str(uuid4()), headers=headers("alice")).json()["contacts"]
    assert names == [{"login": "anton", "name": "Алексей"}]


def test_opt_in_dial_answer_and_end_private_call(calls, fake, tmp_path, monkeypatch):
    client, push = calls
    register(client, "bob", "b" * 22, "Борис")
    monkeypatch.setattr(settings, "android_releases_dir", tmp_path)
    releases = AndroidReleases(tmp_path)
    releases.publish(release(5, b"apk"), BytesIO(b"apk"))
    releases.set_minimum(5)
    legacy_alice = {**headers("alice"), "User-Agent": "okhttp/4.12.0"}
    android_bob = {**headers("bob"), "X-SelfCall-Android-Version": "5"}
    assert client.get("/api/calls/directory", headers=headers("alice")).json() == {
        "incoming": None, "contacts": [{"login": "bob", "name": "Борис"}],
    }
    assert client.get("/api/calls/directory", headers=legacy_alice).status_code == 426
    call_id = str(uuid4())
    root = f"/api/calls/{call_id}"
    body = {"id": call_id, "login": "bob"}
    assert client.post("/api/calls/dial", headers=legacy_alice, json=body).status_code == 426
    for _ in range(2):
        response = client.post("/api/calls/dial", headers=headers("alice"), json=body)
        assert response.json()["status"] == "ringing"
    assert len(push.notifications) == 1
    fid, data = push.notifications[0]
    assert fid == "b" * 22 and data["call_id"] == call_id and data["name"] == "alice"
    assert client.get(root, headers=headers("stranger")).status_code == 404
    assert client.get(root, headers=legacy_alice).status_code == 426
    assert client.post(root + "/connect", headers=headers("bob")).status_code == 409
    assert client.post(root + "/state/accept", headers={**legacy_alice, "X-SelfCall-Android-Version": "5"}).status_code == 403
    assert client.post(root + "/state/accept", headers=headers("bob")).status_code == 426
    assert client.post(root + "/state/accept", headers=android_bob).json()["status"] == "accepted"
    assert client.get(root, headers=headers("alice")).json()["status"] == "accepted"
    assert client.post(root + "/connect", headers=legacy_alice).status_code == 426
    alice = client.post(root + "/connect", headers=headers("alice")).json()
    bob = client.post(root + "/connect", headers=android_bob).json()
    assert alice["meeting_id"] == bob["meeting_id"] and alice["identity"] != bob["identity"]
    assert client.post("/api/meetings/" + alice["meeting_id"] + "/join", json={}).status_code == 404
    assert client.post(root + "/state/end", headers={
        **headers("alice"), "Sec-Fetch-Site": "cross-site",
    }).status_code == 403
    assert client.post(root + "/state/end", headers={
        **headers("alice"), "Sec-Fetch-Site": "same-origin",
    }).json()["status"] == "ended"
    assert alice["meeting_id"] not in fake.rooms
    assert client.post(root + "/connect", headers={**headers("alice"), "X-SelfCall-Android-Version": "5"}).status_code == 409


def test_invalid_firebase_installation_disappears_from_directory(calls):
    client, push = calls
    register(client, "bob", "b" * 22)
    push.result = "unregistered"
    response = client.post("/api/calls/dial", headers=headers("alice"),
                           json={"id": str(uuid4()), "login": "bob"})
    assert response.json()["status"] == "unreachable"
    path = f"/api/calls/directory?device_id={uuid4()}"
    assert client.get(path, headers=headers("alice")).json()["contacts"] == []

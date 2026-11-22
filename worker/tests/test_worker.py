import ipaddress

import pytest

from app import ALLOWED_CIDRS, _parse_ports, _resolve_and_check


def test_parse_ports_accepts_bounded_ranges():
    assert _parse_ports("22,80,443,8000-8002") == "22,80,443,8000,8001,8002"


@pytest.mark.parametrize("value", ["0", "65536", "abc", "1-999", "80,,443", "5-2"])
def test_parse_ports_rejects_invalid_or_oversized(value):
    with pytest.raises(ValueError):
        _parse_ports(value)


def test_target_must_be_in_admin_allowlist(monkeypatch):
    monkeypatch.setattr("app.ALLOWED_CIDRS", [ipaddress.ip_network("192.0.2.0/24")])
    assert _resolve_and_check("192.0.2.10") == ["192.0.2.10"]
    with pytest.raises(ValueError, match="allowlist"):
        _resolve_and_check("198.51.100.10")


def test_empty_allowlist_denies_all(monkeypatch):
    monkeypatch.setattr("app.ALLOWED_CIDRS", [])
    with pytest.raises(ValueError, match="not configured"):
        _resolve_and_check("192.0.2.10")

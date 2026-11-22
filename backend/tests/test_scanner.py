import pytest
from app.scanner import validate_target

def test_reject_localhost():
    with pytest.raises(ValueError):
        validate_target("http://localhost")

def test_reject_credentials():
    with pytest.raises(ValueError):
        validate_target("https://user:pass@example.com")

def test_reject_file_scheme():
    with pytest.raises(ValueError):
        validate_target("file:///etc/passwd")

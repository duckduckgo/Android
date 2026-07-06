import math

from pageload_benchmark import compute_stats, pixel_url


def test_compute_stats_drops_warmup_and_nonpositive():
    # first sample is warmup (dropped); a 0 and a negative are dropped as degenerate
    durations = [999.0, 10.0, 20.0, 30.0, 0.0, -5.0]
    s = compute_stats(durations, warmup=1)
    assert s["count"] == 3
    assert s["median"] == 20.0
    assert s["min"] == 10.0
    assert s["max"] == 30.0
    assert math.isclose(s["mean"], 20.0)


def test_compute_stats_p90():
    s = compute_stats([0.0] + [float(x) for x in range(1, 11)], warmup=1)  # warmup dropped -> 1..10
    assert s["count"] == 10
    assert s["p90"] == 9.0  # nearest-rank 90th percentile of 1..10


def test_pixel_url_shape():
    stats = {"median": 20.0, "mean": 20.0, "std_dev": 8.16, "min": 10.0, "max": 30.0, "p90": 28.0, "count": 3}
    url = pixel_url(stats, run_id="123", sha="abc")
    assert url.startswith("https://improving.duckduckgo.com/t/m_page_load_time_android?")
    assert "median=20.000" in url
    assert "github_action_run_id=123" in url
    assert "git_commit_sha=abc" in url

from app.services.local_time import display_local_time


def test_database_utc_values_are_presented_in_shanghai_without_mutation():
    stored = "2026-08-31 16:51:55"

    assert display_local_time(stored) == "2026-09-01 00:51:55"
    assert stored == "2026-08-31 16:51:55"


def test_iso_utc_cross_month_and_year_are_presented_in_shanghai():
    assert display_local_time("2026-08-31T16:51:55Z") == "2026-09-01 00:51:55"
    assert display_local_time("2026-12-31T18:00:00Z") == "2027-01-01 02:00:00"


def test_offset_aware_value_is_not_double_shifted():
    assert display_local_time("2026-09-01T00:51:55+08:00") == "2026-09-01 00:51:55"

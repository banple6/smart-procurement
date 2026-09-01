from ..database import all_rows


RESOURCE_NAMES = ("orders", "batches", "outbounds", "announcements", "dashboard", "quota")


def resource_revisions(conn) -> dict[str, int]:
    rows = all_rows(conn, "SELECT resource, revision FROM web_resource_revisions")
    values = {row["resource"]: int(row["revision"]) for row in rows}
    return {resource: values.get(resource, 0) for resource in RESOURCE_NAMES}


def bump_resources(conn, *resources: str):
    for resource in dict.fromkeys(resources):
        if resource not in RESOURCE_NAMES:
            raise ValueError(f"unknown realtime resource: {resource}")
        conn.execute(
            """INSERT INTO web_resource_revisions(resource, revision, updated_at) VALUES (?, 1, CURRENT_TIMESTAMP)
               ON CONFLICT(resource) DO UPDATE SET revision = revision + 1, updated_at = CURRENT_TIMESTAMP""",
            (resource,),
        )

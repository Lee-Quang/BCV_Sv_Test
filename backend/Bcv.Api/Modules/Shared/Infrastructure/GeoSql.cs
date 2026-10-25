namespace Bcv.Api.Modules.Shared.Infrastructure;

/// <summary>PostGIS distance in metres between the user's virtual location (v) and a place (p).</summary>
public static class GeoSql
{
    public const string VirtualLocationToPlaceMeters = """
        ST_Distance(
            ST_SetSRID(ST_MakePoint(v.longitude::double precision, v.latitude::double precision), 4326)::geography,
            ST_SetSRID(ST_MakePoint(p.longitude::double precision, p.latitude::double precision), 4326)::geography)
        """;
}

namespace Bcv.Api.Modules.StepLocation.Domain;

/// <summary>
/// Virtual distance to steps (design 3.5.1.2): S = max(1, ⌈1000 · D · κ / L⌉) with D in km.
/// κ scales real distances down so a trip between places fits daily walking (50 km → 6,667 steps).
/// Only new journeys use it; a journey keeps the steps it was created with.
/// </summary>
public static class StepConversion
{
    /// <summary>L: conventional step length in metres.</summary>
    public const decimal StepLengthMeters = 0.75m;
    /// <summary>κ: exploration scale, 0 &lt; κ ≤ 1.</summary>
    public const decimal DistanceScale = 0.1m;

    public static int RequiredSteps(decimal distanceKm) =>
        (int)Math.Clamp(Math.Ceiling(distanceKm * 1000m * DistanceScale / StepLengthMeters), 1m, int.MaxValue);
}

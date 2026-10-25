namespace Bcv.Api.Modules.StepLocation.Domain;

/// <summary>Virtual distance to steps: one step covers 0.75 m, and a trip is at least one step.</summary>
public static class StepConversion
{
    public static int RequiredSteps(decimal distanceKm) =>
        (int)Math.Clamp(Math.Ceiling(distanceKm * 1000m / 0.75m), 1m, int.MaxValue);
}

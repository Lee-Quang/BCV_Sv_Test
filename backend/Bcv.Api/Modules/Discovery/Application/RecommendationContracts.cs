using Bcv.Api.Modules.Discovery.Domain;

namespace Bcv.Api.Modules.Discovery.Application;

public sealed record RecommendationConfig(long ConfigId, string Version, decimal DistanceLimitMeters,
    IReadOnlyDictionary<string, decimal> Weights);

public sealed record RecommendationInput(string RegionName, decimal Latitude, decimal Longitude, string? ActivityLevel,
    IReadOnlyList<PreferenceGroup> Preferences, RecommendationConfig Config,
    IReadOnlyList<RecommendationPlace> Places);

public sealed record RecommendedPlace(int Rank, long PlaceId, string Name, string RegionName, string? ImageUrl,
    bool IsDiscovered, decimal DistanceKm, int RequiredSteps, double Score,
    IReadOnlyList<string> MatchedPreferences, CriterionScores Criteria,
    IReadOnlyList<string> UsedCriteria, IReadOnlyList<string> MissingCriteria);

/// <summary>Latitude/Longitude are the virtual location, so the app can show its street address.</summary>
public sealed record RecommendationResult(string RegionName, decimal Latitude, decimal Longitude,
    string ConfigVersion, IReadOnlyList<RecommendedPlace> Places);

public interface IRecommendationRepository
{
    /// <summary>Null when the user has no virtual location yet.</summary>
    Task<RecommendationInput?> LoadAsync(long userId, CancellationToken ct);
}

public sealed class RecommendationService(IRecommendationRepository repository)
{
    public async Task<RecommendationResult> RecommendAsync(long userId, CancellationToken ct)
    {
        var input = await repository.LoadAsync(userId, ct)
            ?? throw new ExploreException("NO_VIRTUAL_LOCATION", "Hãy chọn điểm xuất phát trước khi tìm điểm đến.");
        var ranked = RecommendationScoring.Rank(input.Places, input.Preferences, input.ActivityLevel,
            input.Config.Weights, input.Config.DistanceLimitMeters);
        var places = ranked.Select((p, index) =>
        {
            var used = Criteria(p.Criteria).Where(c => c.Value is not null).Select(c => c.Code).ToList();
            var missing = Criteria(p.Criteria).Where(c => c.Value is null).Select(c => c.Code).ToList();
            return new RecommendedPlace(index + 1, p.Place.PlaceId, p.Place.Name, p.Place.RegionName,
                p.Place.ImageUrl, p.Place.Discovered, Math.Round(p.Place.DistanceMeters / 1000m, 2),
                p.RequiredSteps, Math.Round(p.Score, 4), p.MatchedPreferences, p.Criteria, used, missing);
        }).ToList();
        return new RecommendationResult(input.RegionName, input.Latitude, input.Longitude,
            input.Config.Version, places);
    }

    private static (string Code, double? Value)[] Criteria(CriterionScores s) =>
    [
        (RecommendationScoring.Preference, s.Preference), (RecommendationScoring.Cluster, s.Cluster),
        (RecommendationScoring.Activity, s.Activity), (RecommendationScoring.Rating, s.Rating),
        (RecommendationScoring.Popularity, s.Popularity)
    ];
}

using Bcv.Api.Modules.StepLocation.Domain;

namespace Bcv.Api.Modules.Discovery.Domain;

/// <summary>One active place with the model data the ranking needs (section 3.5.4.6 of the design).</summary>
public sealed record RecommendationPlace(
    long PlaceId, string Name, string RegionName, string? ImageUrl,
    IReadOnlySet<string> Features, int? Cluster, decimal? Rating, int? RatingCount,
    decimal DistanceMeters, bool Discovered);

/// <summary>A preference the user picked; a place matches it when it has any of [FeatureCodes].</summary>
public sealed record PreferenceGroup(string Name, IReadOnlySet<string> FeatureCodes)
{
    public bool Matches(RecommendationPlace place) => place.Features.Overlaps(FeatureCodes);
}

public sealed record CriterionScores(double? Preference, double? Cluster, double? Activity,
    double? Rating, double? Popularity);

public sealed record ScoredPlace(RecommendationPlace Place, int RequiredSteps, double Score,
    CriterionScores Criteria, IReadOnlyList<string> MatchedPreferences);

/// <summary>
/// P/C/A/R/N ranking. Missing criteria are dropped and the remaining weights renormalised;
/// ties break on place id so the order is stable.
/// </summary>
public static class RecommendationScoring
{
    public const string Preference = "PREFERENCE";
    public const string Cluster = "CLUSTER";
    public const string Activity = "ACTIVITY";
    public const string Rating = "RATING";
    public const string Popularity = "POPULARITY";
    public const int MaxResults = 3;

    /// <summary>T(u): conventional daily steps per activity level.</summary>
    public static readonly IReadOnlyDictionary<string, int> ActivityThresholds = new Dictionary<string, int>
    {
        ["LOW"] = 3000, ["MEDIUM"] = 6000, ["HIGH"] = 10000
    };

    /// <summary>
    /// The onboarding interests are the 12 standard place types (db/014_standard_place_types.sql,
    /// fixed ids 101-112). A type is satisfied by any of its 35-feature model columns, like
    /// "Biển đảo = coast or island" in the design. Keyed by id, so renaming a type in the admin web
    /// keeps the mapping; a type not listed here does not count towards P.
    /// </summary>
    public static readonly IReadOnlyDictionary<long, IReadOnlySet<string>> PlaceTypeFeatures =
        new Dictionary<long, IReadOnlySet<string>>
    {
        [101] = Set("kind__heritage", "kind__museum"),                      // Di tích và bảo tàng
        [102] = Set("kind__religious"),                                     // Đền, chùa và nhà thờ
        [103] = Set("kind__structure"),                                     // Công trình kiến trúc
        [104] = Set("kind__community", "kind__craft"),                      // Phố cổ và làng nghề
        [105] = Set("kind__market", "theme__cuisine"),                      // Chợ và ẩm thực
        [106] = Set("kind__recreation"),                                    // Vui chơi và nghỉ dưỡng
        [107] = Set("kind__agriculture"),                                   // Đồng quê và nông nghiệp
        [108] = Set("landform__coast", "landform__island", "landform__lagoon", "landform__dunes"), // Biển và đảo
        [109] = Set("landform__river", "landform__lake", "landform__waterfall", "landform__stream"), // Sông, hồ và thác
        [110] = Set("landform__mountain", "landform__valley"),              // Núi và đèo
        [111] = Set("landform__cave"),                                      // Hang động
        [112] = Set("ecosystem__forest", "ecosystem__flooded_habitat", "ecosystem__grassland") // Rừng và khu bảo tồn
    };

    private static IReadOnlySet<string> Set(params string[] codes) => codes.ToHashSet();

    /// <param name="places">Every active place with features: the reference set for C and N_max.</param>
    public static IReadOnlyList<ScoredPlace> Rank(IReadOnlyList<RecommendationPlace> places,
        IReadOnlyList<PreferenceGroup> preferences, string? activityLevel,
        IReadOnlyDictionary<string, decimal> weights, decimal distanceLimitMeters)
    {
        // P(u, p) for every place; C(u, c) is the mean P over the distinct places of a cluster.
        var preference = places.ToDictionary(p => p.PlaceId, p => PreferenceScore(p, preferences));
        var clusterScores = places.Where(p => p.Cluster is not null && preference[p.PlaceId] is not null)
            .GroupBy(p => p.Cluster!.Value)
            .ToDictionary(g => g.Key, g => g.Average(p => preference[p.PlaceId]!.Value));

        // N_max comes from the reference set before the per-user distance and discovery filters.
        var maxCount = places.Where(HasRatingCount).Select(p => p.RatingCount!.Value).DefaultIfEmpty(0).Max();
        int? threshold = activityLevel is not null && ActivityThresholds.TryGetValue(activityLevel, out var t) ? t : null;

        var ranked = new List<ScoredPlace>();
        foreach (var place in places)
        {
            if (place.Discovered || place.DistanceMeters <= 0 || place.DistanceMeters > distanceLimitMeters) continue;
            var steps = StepConversion.RequiredSteps(place.DistanceMeters / 1000m);
            var criteria = new CriterionScores(
                preference[place.PlaceId],
                place.Cluster is int c && clusterScores.TryGetValue(c, out var cs) ? cs : null,
                threshold is int limit ? Math.Min(1.0, (double)limit / steps) : null,
                place.Rating is decimal r && r is >= 0 and <= 5 ? (double)(r / 5m) : null,
                HasRatingCount(place) && maxCount > 0
                    ? Math.Log(1 + place.RatingCount!.Value) / Math.Log(1 + maxCount) : null);
            var score = Combine(criteria, weights);
            if (score is null) continue;
            var matched = preferences.Where(g => g.Matches(place)).Select(g => g.Name).ToList();
            ranked.Add(new ScoredPlace(place, steps, score.Value, criteria, matched));
        }
        return ranked.OrderByDescending(p => p.Score).ThenBy(p => p.Place.PlaceId).Take(MaxResults).ToList();
    }

    private static double? PreferenceScore(RecommendationPlace place, IReadOnlyList<PreferenceGroup> preferences) =>
        preferences.Count == 0 ? null
            : (double)preferences.Count(g => g.Matches(place)) / preferences.Count;

    /// <summary>The import stored a missing count as 0, so a place with neither rating nor count has no data.</summary>
    private static bool HasRatingCount(RecommendationPlace place) =>
        place.RatingCount is >= 0 && !(place.Rating is null && place.RatingCount == 0);

    /// <summary>S(u, p) = Σ w·s / Σ w over the criteria that have data.</summary>
    public static double? Combine(CriterionScores criteria, IReadOnlyDictionary<string, decimal> weights)
    {
        double total = 0, weightSum = 0;
        void Add(string code, double? value)
        {
            if (value is null || !weights.TryGetValue(code, out var w) || w <= 0) return;
            total += (double)w * value.Value;
            weightSum += (double)w;
        }
        Add(Preference, criteria.Preference);
        Add(Cluster, criteria.Cluster);
        Add(Activity, criteria.Activity);
        Add(Rating, criteria.Rating);
        Add(Popularity, criteria.Popularity);
        return weightSum > 0 ? total / weightSum : null;
    }
}

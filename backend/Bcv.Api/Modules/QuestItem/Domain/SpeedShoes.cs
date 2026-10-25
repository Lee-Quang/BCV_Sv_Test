using System.Globalization;

namespace Bcv.Api.Modules.QuestItem.Domain;

/// <summary>
/// Speed-shoe boosts are kept without new tables: activation writes a zero-step STEP_DATA row whose
/// source id records the journey, the journey's raw step count at activation and the item's effect,
/// e.g. "speed-shoes:journey:42:from:1300:x2.00:r5000". Step sync multiplies the raw steps that fall
/// in [from, from + range) — boosted steps only count toward that journey, never toward step totals.
/// </summary>
public static class SpeedShoes
{
    public const string Type = "SPEED_SHOES";
    public const string Prefix = "speed-shoes:journey:";

    public static string SourceId(long journeyId, int from, decimal multiplier, int range) =>
        $"{Prefix}{journeyId}:from:{from}:x{multiplier.ToString("0.00", CultureInfo.InvariantCulture)}:r{range}";

    public static string HealthSourceId(long journeyId) => $"health-connect:journey:{journeyId}";

    public sealed record Boost(int From, decimal Multiplier, int Range);

    /// <summary>Extra journey steps earned when raw steps move from <paramref name="previous"/> to <paramref name="current"/>.</summary>
    public static int Bonus(IEnumerable<Boost> boosts, int previous, int current)
    {
        decimal bonus = 0;
        foreach (var boost in boosts)
        {
            var boosted = Math.Min(current, boost.From + boost.Range) - Math.Max(previous, boost.From);
            if (boosted > 0) bonus += boosted * (boost.Multiplier - 1m);
        }
        return (int)Math.Floor(Math.Max(0, bonus));
    }
}

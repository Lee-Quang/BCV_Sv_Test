namespace Bcv.Api.Modules.QuestItem.Application;

public sealed record SpeedShoesJourney(long JourneyId, string DestinationName, int RecordedSteps, int RequiredSteps);

/// <summary>A boost started on the current journey; RemainingSteps &gt; 0 while it still applies.</summary>
public sealed record SpeedShoesEffect(decimal Multiplier, int Range, int RemainingSteps);

public sealed record SpeedShoesStatus(long ItemId, string Name, bool Active, int Quantity, decimal Multiplier,
    int Range, SpeedShoesJourney? Journey, SpeedShoesEffect? Effect);

public sealed record RegionOption(long RegionId, string Name, int PlaceCount);

public sealed record FlightTicketStatus(long ItemId, string Name, bool Active, int Quantity, bool HasActiveJourney,
    string? CurrentRegion, long? CurrentRegionId, IReadOnlyList<RegionOption> Regions);

public sealed record FlightPlace(long PlaceId, string Name, bool Discovered);

public sealed record UsePlaceRequest(long PlaceId);

public sealed record FlightResult(string Region, string Place, int Quantity);

public sealed class ItemUseException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

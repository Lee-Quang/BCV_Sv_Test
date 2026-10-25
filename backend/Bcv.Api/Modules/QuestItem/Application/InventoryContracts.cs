namespace Bcv.Api.Modules.QuestItem.Application;

/// <summary>An item type with how many the user holds; unowned types are listed with quantity 0.</summary>
public sealed record InventoryItem(long ItemId, string Name, string ItemType, string? Description,
    decimal? EffectValue, int? EffectiveRange, int? UsageLimit, bool Active, int Quantity);

/// <summary>HasActiveJourney drives the usage conditions: shoes need one, a flight ticket needs none.</summary>
public sealed record InventoryOverview(int TotalQuantity, bool HasActiveJourney, bool BoostActive,
    IReadOnlyList<InventoryItem> Items);

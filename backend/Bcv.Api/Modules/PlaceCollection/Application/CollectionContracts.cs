namespace Bcv.Api.Modules.PlaceCollection.Application;

public sealed record CollectionSummary(long CollectionId, string Name, string? Description,
    int OwnedCards, int TotalCards, IReadOnlyList<string> PreviewImageUrls);

public sealed record CollectionOverview(int OwnedCards, int TotalCards, IReadOnlyList<CollectionSummary> Collections);

/// <summary>A card in a collection. ImageUrl is only sent once owned, like a locked place's content.</summary>
public sealed record CollectionCard(long CardId, long PlaceId, string Name, string? ImageUrl, bool Owned);

public sealed record CollectionDetail(long CollectionId, string Name, string? Description,
    int OwnedCards, int TotalCards, IReadOnlyList<CollectionCard> Cards);

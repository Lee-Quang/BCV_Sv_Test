namespace Bcv.Api.Modules.StatsNotify.Application;

public sealed record QuestTally(int Completed, int Total);

public sealed record ProfileStats(long TotalSteps, int DiscoveredPlaces, int OwnedCards, QuestTally Daily, QuestTally Weekly);

public sealed record NotificationView(long NotificationId, string Type, string Title, string Content, bool Read,
    DateTime CreatedAt);

public sealed record NotificationList(int UnreadCount, IReadOnlyList<NotificationView> Items);

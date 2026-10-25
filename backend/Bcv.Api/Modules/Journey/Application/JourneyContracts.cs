using Bcv.Api.Modules.Discovery.Application;

namespace Bcv.Api.Modules.Journey.Application;

public sealed record SyncStepsCommand(long JourneyId, long CumulativeSteps);

public sealed record CreateJourneyCommand(long PlaceId, bool ReplaceActive = false);

public sealed record JourneyHistoryEntry(long JourneyId, string Status, string StartName, long DestinationPlaceId,
    string DestinationName, DateTime StartedAt, DateTime? CompletedAt);

public sealed class JourneyException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

public interface IJourneyRepository
{
    Task CreateJourneyAsync(long userId, CreateJourneyCommand command, CancellationToken ct);
    Task SyncStepsAsync(long userId, SyncStepsCommand command, CancellationToken ct);
    Task<IReadOnlyList<JourneyHistoryEntry>> HistoryAsync(long userId, CancellationToken ct);
}

/// <summary>
/// Journey commands. Each answers with the refreshed explore snapshot, which the Discovery module reads
/// after the journey change has been committed.
/// </summary>
public sealed class JourneyService(IJourneyRepository repository, IExploreRepository explore)
{
    public async Task<ExploreSnapshot> CreateJourneyAsync(long userId, CreateJourneyCommand command, CancellationToken ct)
    {
        if (command.PlaceId <= 0) throw new JourneyException("INVALID_PLACE", "Địa danh không hợp lệ.");
        await repository.CreateJourneyAsync(userId, command, ct);
        return await explore.LoadAsync(userId, ct);
    }

    public async Task<ExploreSnapshot> SyncStepsAsync(long userId, SyncStepsCommand command, CancellationToken ct)
    {
        if (command.JourneyId <= 0 || command.CumulativeSteps is < 0 or > 50_000_000)
            throw new JourneyException("INVALID_STEPS", "Dữ liệu bước chân không hợp lệ.");
        await repository.SyncStepsAsync(userId, command, ct);
        return await explore.LoadAsync(userId, ct);
    }

    public Task<IReadOnlyList<JourneyHistoryEntry>> HistoryAsync(long userId, CancellationToken ct) =>
        repository.HistoryAsync(userId, ct);
}

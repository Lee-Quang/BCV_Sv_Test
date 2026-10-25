namespace Bcv.Api.Modules.ActivityLevel.Application;

public sealed record ActivityLevelSnapshot(string? ActivityLevel);

public sealed class ActivityLevelException(string code, string message) : Exception(message)
{
    public string Code { get; } = code;
}

public interface IActivityLevelRepository
{
    Task<ActivityLevelSnapshot> LoadAsync(long userId, CancellationToken ct);
    Task SaveAsync(long userId, string level, CancellationToken ct);
}

public sealed class ActivityLevelService(IActivityLevelRepository repository)
{
    private static readonly HashSet<string> ValidLevels = ["LOW", "MEDIUM", "HIGH"];

    public Task<ActivityLevelSnapshot> LoadAsync(long userId, CancellationToken ct) =>
        repository.LoadAsync(userId, ct);

    public async Task<string> SaveAsync(long userId, string? level, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(level))
            throw new ActivityLevelException("ACTIVITY_LEVEL_REQUIRED", "Hãy chọn một mức vận động.");
        if (!ValidLevels.Contains(level))
            throw new ActivityLevelException("INVALID_ACTIVITY_LEVEL", "Mức vận động không hợp lệ.");
        await repository.SaveAsync(userId, level, ct);
        return level;
    }
}

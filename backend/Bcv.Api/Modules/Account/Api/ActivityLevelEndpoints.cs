using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Shared.Api;

namespace Bcv.Api.Modules.Account.Api;

public sealed record SaveActivityLevelRequest(string? ActivityLevel);

public static class ActivityLevelEndpoints
{
    public static void MapActivityLevelEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/activity-level");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions,
            ActivityLevelService service, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            return user is null ? Results.Unauthorized() : Results.Ok(await service.LoadAsync(user.UserId, ct));
        });
        group.MapPut("", async (HttpRequest request, SaveActivityLevelRequest body,
            ISessionStore sessions, ActivityLevelService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            if (user is null) return Results.Unauthorized();
            try
            {
                var level = await service.SaveAsync(user.UserId, body.ActivityLevel, ct);
                return Results.Ok(new ActivityLevelSnapshot(level));
            }
            catch (ActivityLevelException ex)
            {
                return Results.Json(new ApiError(ex.Code, ex.Message), statusCode: 400);
            }
            catch (Exception ex)
            {
                logs.CreateLogger("ActivityLevel").LogError(ex, "Saving activity level failed");
                return Results.Json(new ApiError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."), statusCode: 500);
            }
        });
    }
}

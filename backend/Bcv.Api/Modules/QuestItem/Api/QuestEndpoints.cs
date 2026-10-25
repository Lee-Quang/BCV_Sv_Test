using Bcv.Api.Modules.Account.Api;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.QuestItem.Infrastructure;
using Bcv.Api.Modules.Shared.Api;

namespace Bcv.Api.Modules.QuestItem.Api;

public static class QuestEndpoints
{
    public static void MapQuestEndpoints(this WebApplication app)
    {
        app.MapGet("/api/quests", async (HttpRequest request, ISessionStore sessions,
            QuestRepository repository, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            if (user is null) return Results.Unauthorized();
            request.HttpContext.Response.Headers.CacheControl = "no-store";
            try { return Results.Ok(await repository.LoadAsync(user.UserId, ct)); }
            catch (Exception ex)
            {
                logs.CreateLogger("Quest").LogError(ex, "Quest request failed");
                return Results.Json(new ApiError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                    statusCode: 500);
            }
        });
    }
}

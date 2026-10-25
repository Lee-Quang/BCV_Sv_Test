using Bcv.Api.Modules.Account.Api;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Discovery.Application;
using Bcv.Api.Modules.Shared.Api;

namespace Bcv.Api.Modules.Discovery.Api;

public static class ExploreEndpoints
{
    public static void MapExploreEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/explore");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions,
            ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.LoadAsync(user.UserId, ct), logs);
        });
        group.MapGet("/places/{placeId:long}", async (long placeId, HttpRequest request, ISessionStore sessions,
            ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.LoadPlaceAsync(user.UserId, placeId, ct), logs);
        });
    }

    private static async Task<IResult> Execute<T>(Func<Task<T>> action, ILoggerFactory logs)
    {
        try { return Results.Ok(await action()); }
        catch (ExploreException ex)
        {
            return Results.Json(new ApiError(ex.Code, ex.Message), statusCode: 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("Explore").LogError(ex, "Explore request failed");
            return Results.Json(new ApiError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                statusCode: 500);
        }
    }
}

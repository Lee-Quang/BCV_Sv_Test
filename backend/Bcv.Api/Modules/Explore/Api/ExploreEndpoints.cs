using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Explore.Application;

namespace Bcv.Api.Modules.Explore.Api;

public sealed record ExploreError(string Code, string Message);

public static class ExploreEndpoints
{
    public static void MapExploreEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/explore");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions,
            ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.LoadAsync(user.UserId, ct), logs);
        });
        group.MapGet("/places/{placeId:long}", async (long placeId, HttpRequest request, ISessionStore sessions,
            ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.LoadPlaceAsync(user.UserId, placeId, ct), logs);
        });
        group.MapPost("/journeys", async (HttpRequest request, CreateJourneyCommand command,
            ISessionStore sessions, ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.CreateJourneyAsync(user.UserId, command, ct), logs);
        });
        group.MapPost("/sync-steps", async (HttpRequest request, SyncStepsCommand command,
            ISessionStore sessions, ExploreService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() :
                await Execute(() => service.SyncStepsAsync(user.UserId, command, ct), logs);
        });
    }

    private static async Task<IResult> Execute<T>(Func<Task<T>> action, ILoggerFactory logs)
    {
        try { return Results.Ok(await action()); }
        catch (ExploreException ex)
        {
            return Results.Json(new ExploreError(ex.Code, ex.Message), statusCode: 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("Explore").LogError(ex, "Explore request failed");
            return Results.Json(new ExploreError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."),
                statusCode: 500);
        }
    }

    private static string? Bearer(HttpRequest request)
    {
        var value = request.Headers.Authorization.ToString();
        return value.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? value[7..] : null;
    }
}

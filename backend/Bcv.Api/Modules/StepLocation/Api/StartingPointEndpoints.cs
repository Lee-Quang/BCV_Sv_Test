using Bcv.Api.Modules.Account.Api;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Shared.Api;
using Bcv.Api.Modules.StepLocation.Application;

namespace Bcv.Api.Modules.StepLocation.Api;

public sealed record CoordinatesRequest(decimal? Latitude, decimal? Longitude);

public static class StartingPointEndpoints
{
    public static void MapStartingPointEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/starting-point");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions,
            StartingPointService service, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            return user is null ? Results.Unauthorized() : Results.Ok(await service.LoadAsync(user.UserId, ct));
        });
        group.MapPost("/resolve-current", async (HttpRequest request, CoordinatesRequest body,
            ISessionStore sessions, StartingPointService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            if (CurrentUser.Get(request, sessions) is null) return Results.Unauthorized();
            return await Execute(() => service.ResolveCurrentAsync(body.Latitude, body.Longitude, ct), logs);
        });
        group.MapPut("", async (HttpRequest request, SaveStartingPointCommand body,
            ISessionStore sessions, StartingPointService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = CurrentUser.Get(request, sessions);
            if (user is null) return Results.Unauthorized();
            return await Execute(() => service.SaveAsync(user.UserId, body, ct), logs);
        });
    }

    private static async Task<IResult> Execute<T>(Func<Task<T>> action, ILoggerFactory logs)
    {
        try { return Results.Ok(await action()); }
        catch (StartingPointException ex)
        {
            return Results.Json(new ApiError(ex.Code, ex.Message), statusCode: 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("StartingPoint").LogError(ex, "Starting point request failed");
            return Results.Json(new ApiError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."), statusCode: 500);
        }
    }
}

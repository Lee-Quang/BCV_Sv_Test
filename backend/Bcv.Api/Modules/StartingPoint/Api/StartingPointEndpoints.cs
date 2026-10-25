using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.StartingPoint.Application;

namespace Bcv.Api.Modules.StartingPoint.Api;

public sealed record CoordinatesRequest(decimal? Latitude, decimal? Longitude);
public sealed record StartingPointError(string Code, string Message);

public static class StartingPointEndpoints
{
    public static void MapStartingPointEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/starting-point");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions,
            StartingPointService service, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() : Results.Ok(await service.LoadAsync(user.UserId, ct));
        });
        group.MapPost("/resolve-current", async (HttpRequest request, CoordinatesRequest body,
            ISessionStore sessions, StartingPointService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            if (sessions.Get(Bearer(request)) is null) return Results.Unauthorized();
            return await Execute(() => service.ResolveCurrentAsync(body.Latitude, body.Longitude, ct), logs);
        });
        group.MapPut("", async (HttpRequest request, SaveStartingPointCommand body,
            ISessionStore sessions, StartingPointService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            if (user is null) return Results.Unauthorized();
            return await Execute(() => service.SaveAsync(user.UserId, body, ct), logs);
        });
    }

    private static async Task<IResult> Execute<T>(Func<Task<T>> action, ILoggerFactory logs)
    {
        try { return Results.Ok(await action()); }
        catch (StartingPointException ex)
        {
            return Results.Json(new StartingPointError(ex.Code, ex.Message), statusCode: 400);
        }
        catch (Exception ex)
        {
            logs.CreateLogger("StartingPoint").LogError(ex, "Starting point request failed");
            return Results.Json(new StartingPointError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."), statusCode: 500);
        }
    }

    private static string? Bearer(HttpRequest request)
    {
        var value = request.Headers.Authorization.ToString();
        return value.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? value[7..] : null;
    }
}

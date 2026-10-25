using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Interests.Application;

namespace Bcv.Api.Modules.Interests.Api;

public sealed record SaveInterestsRequest(long[]? PlaceTypeIds);
public sealed record InterestError(string Code, string Message);

public static class InterestEndpoints
{
    public static void MapInterestEndpoints(this WebApplication app)
    {
        var group = app.MapGroup("/api/interests");
        group.MapGet("", async (HttpRequest request, ISessionStore sessions, InterestService service, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            return user is null ? Results.Unauthorized() : Results.Ok(await service.LoadAsync(user.UserId, ct));
        });
        group.MapPut("", async (HttpRequest request, SaveInterestsRequest body, ISessionStore sessions,
            InterestService service, ILoggerFactory logs, CancellationToken ct) =>
        {
            var user = sessions.Get(Bearer(request));
            if (user is null) return Results.Unauthorized();
            try
            {
                var selected = await service.SaveAsync(user.UserId, body.PlaceTypeIds, ct);
                return Results.Ok(new { selectedPlaceTypeIds = selected });
            }
            catch (InterestException ex)
            {
                return Results.Json(new InterestError(ex.Code, ex.Message), statusCode: 400);
            }
            catch (Exception ex)
            {
                logs.CreateLogger("Interests").LogError(ex, "Saving interests failed");
                return Results.Json(new InterestError("SERVER_ERROR", "Máy chủ đang gặp lỗi. Vui lòng thử lại."), statusCode: 500);
            }
        });
    }

    private static string? Bearer(HttpRequest request)
    {
        var value = request.Headers.Authorization.ToString();
        return value.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? value[7..] : null;
    }
}

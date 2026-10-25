using Bcv.Api.Modules.Account.Api;
using Bcv.Api.Modules.Account.Application;
using Bcv.Api.Modules.Account.Infrastructure;
using Bcv.Api.Modules.ActivityLevel.Api;
using Bcv.Api.Modules.ActivityLevel.Application;
using Bcv.Api.Modules.ActivityLevel.Infrastructure;
using Bcv.Api.Modules.Interests.Api;
using Bcv.Api.Modules.Interests.Application;
using Bcv.Api.Modules.Interests.Infrastructure;
using Bcv.Api.Modules.Collection;
using Bcv.Api.Modules.Quest;
using Bcv.Api.Modules.Inventory;
using Bcv.Api.Modules.Profile;
using Bcv.Api.Modules.Explore.Api;
using Bcv.Api.Modules.Explore.Application;
using Bcv.Api.Modules.Explore.Infrastructure;
using Bcv.Api.Modules.StartingPoint.Api;
using Bcv.Api.Modules.StartingPoint.Application;
using Bcv.Api.Modules.StartingPoint.Infrastructure;
using Npgsql;

var builder = WebApplication.CreateBuilder(args);
var connectionString = builder.Configuration.GetConnectionString("Bcv")
    ?? throw new InvalidOperationException("Set ConnectionStrings__Bcv before starting the API.");

builder.Services.AddSingleton(NpgsqlDataSource.Create(connectionString));
builder.Services.AddScoped<IAccountRepository, PostgresAccountRepository>();
builder.Services.AddScoped<AccountService>();
builder.Services.AddSingleton<ISessionStore, InMemorySessionStore>();
builder.Services.AddSingleton<IPasswordResetStore, InMemoryPasswordResetStore>();
builder.Services.AddScoped<PasswordResetService>();
var smtp = builder.Configuration.GetSection("Smtp");
if (!string.IsNullOrWhiteSpace(smtp["Host"]))
{
    builder.Services.AddSingleton(new SmtpSettings(smtp["Host"]!, smtp.GetValue("Port", 587), smtp["User"],
        smtp["Password"], smtp["From"] ?? smtp["User"] ?? "no-reply@buocchanviet.local", smtp.GetValue("EnableSsl", true)));
    builder.Services.AddSingleton<IPasswordResetMailer, SmtpPasswordResetMailer>();
}
else
{
    builder.Services.AddSingleton<IPasswordResetMailer, LogPasswordResetMailer>();
}
builder.Services.AddScoped<IInterestRepository, PostgresInterestRepository>();
builder.Services.AddScoped<InterestService>();
builder.Services.AddScoped<IActivityLevelRepository, PostgresActivityLevelRepository>();
builder.Services.AddScoped<ActivityLevelService>();
builder.Services.AddScoped<IStartingPointRepository, PostgresStartingPointRepository>();
builder.Services.AddScoped<StartingPointService>();
builder.Services.AddScoped<IExploreRepository, PostgresExploreRepository>();
builder.Services.AddScoped<ExploreService>();
builder.Services.AddScoped<CollectionRepository>();
builder.Services.AddScoped<QuestRepository>();
builder.Services.AddScoped<InventoryRepository>();
builder.Services.AddScoped<ItemUseRepository>();
builder.Services.AddScoped<ProfileRepository>();
// Avatars go to Cloudflare R2 when R2__* is set (see run-local.ps1); otherwise they stay inline in the database.
var r2 = builder.Configuration.GetSection("R2");
if (new[] { "AccountId", "Bucket", "AccessKeyId", "SecretAccessKey", "PublicBaseUrl" }.All(k => !string.IsNullOrWhiteSpace(r2[k])))
{
    builder.Services.AddSingleton(new R2Settings(r2["AccountId"]!, r2["Bucket"]!, r2["AccessKeyId"]!,
        r2["SecretAccessKey"]!, r2["PublicBaseUrl"]!));
    builder.Services.AddHttpClient<IAvatarStorage, R2AvatarStorage>();
}
else
{
    builder.Services.AddSingleton<IAvatarStorage, InlineAvatarStorage>();
}

var app = builder.Build();
app.MapGet("/health", async (NpgsqlDataSource db, CancellationToken cancellationToken) =>
{
    try
    {
        await using var command = db.CreateCommand("SELECT 1");
        await command.ExecuteScalarAsync(cancellationToken);
        return Results.Ok(new { status = "ok" });
    }
    catch
    {
        return Results.Problem("Database unavailable", statusCode: 503);
    }
});
app.MapAccountEndpoints();
app.MapInterestEndpoints();
app.MapActivityLevelEndpoints();
app.MapStartingPointEndpoints();
app.MapExploreEndpoints();
app.MapCollectionEndpoints();
app.MapQuestEndpoints();
app.MapInventoryEndpoints();
app.MapItemUseEndpoints();
app.MapProfileEndpoints();
app.Run();

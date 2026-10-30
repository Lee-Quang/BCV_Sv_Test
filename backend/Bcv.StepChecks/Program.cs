using Bcv.Api.Modules.Discovery.Infrastructure;
using Bcv.Api.Modules.Journey.Application;
using Bcv.Api.Modules.Journey.Infrastructure;
using Bcv.Api.Modules.QuestItem.Application;
using Bcv.Api.Modules.QuestItem.Infrastructure;
using Npgsql;

var settings = new NpgsqlConnectionStringBuilder(Environment.GetEnvironmentVariable("BCV_STEP_CHECK_CONNECTION")
    ?? throw new Exception("Set BCV_STEP_CHECK_CONNECTION to an isolated copy of the database."));
if (!settings.Database!.StartsWith("bcv_step_checks_", StringComparison.Ordinal))
    throw new Exception("Only isolated bcv_step_checks_* databases are allowed. Never point these fixtures at a live database.");
await using var db = NpgsqlDataSource.Create(settings.ConnectionString);
void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
async Task<object?> Sql(string text, params object[] parameters)
{
    await using var command = db.CreateCommand(text);
    foreach (var parameter in parameters) command.Parameters.AddWithValue(parameter);
    return await command.ExecuteScalarAsync();
}
var user = (long)(await Sql("""
    INSERT INTO app_user(full_name,email,password_hash,role,status)
    VALUES('Step test fixture',$1,'not-a-login-hash','USER','ACTIVE') RETURNING user_id
    """, $"step-check-{Guid.NewGuid():N}@example.invalid"))!;
await Sql("UPDATE item SET status='ACTIVE' WHERE item_type IN ('SPEED_SHOES','FLIGHT_TICKET')");
var shoesId = (long)(await Sql("SELECT item_id FROM item WHERE item_type='SPEED_SHOES' ORDER BY item_id LIMIT 1"))!;
var ticketId = (long)(await Sql("SELECT item_id FROM item WHERE item_type='FLIGHT_TICKET' ORDER BY item_id LIMIT 1"))!;
await Sql("UPDATE item SET effect_value=2,effective_range=5000 WHERE item_id=$1", shoesId);
await Sql("INSERT INTO user_item(user_id,item_id,quantity) VALUES($1,$2,2),($1,$3,2)", user, shoesId, ticketId);
var from = (long)(await Sql("SELECT place_id FROM place WHERE status='ACTIVE' ORDER BY place_id LIMIT 1"))!;
var to = (long)(await Sql("SELECT place_id FROM place WHERE status='ACTIVE' AND place_id<>$1 ORDER BY place_id LIMIT 1", from))!;
await Sql("""
    INSERT INTO virtual_location(user_id,region_id,nearest_place_id,latitude,longitude)
    SELECT $1,region_id,place_id,latitude,longitude FROM place WHERE place_id=$2
    """, user, from);
var trip = (long)(await Sql("""
    INSERT INTO journey(user_id,start_place_id,start_latitude,start_longitude,destination_place_id,distance,required_steps,status)
    SELECT $1,place_id,latitude,longitude,$3,1,1000,'IN_PROGRESS' FROM place WHERE place_id=$2 RETURNING journey_id
    """, user, from, to))!;
var quest = (long)(await Sql("""
    INSERT INTO quest(reward_item_id,name,quest_type,condition_type,target_value,cycle_type,reward_quantity,status)
    VALUES($1,'Step check: 400 raw steps','DAILY','STEP_COUNT',400,'DAILY',2,'ACTIVE') RETURNING quest_id
    """, shoesId))!;
var explore = new PostgresExploreRepository(db);
var service = new JourneyService(new PostgresJourneyRepository(db), explore);
var items = new ItemUseRepository(db);
var quests = new QuestRepository(db);
async Task<int> Quantity(long item) => (int)(await Sql("SELECT quantity FROM user_item WHERE user_id=$1 AND item_id=$2", user, item))!;
async Task<int> QuestSteps() => (int)(await Sql("SELECT progress_value FROM quest_progress WHERE user_id=$1 AND quest_id=$2", user, quest))!;

var first = await service.SyncStepsAsync(user, new(trip, 200), default);
Check(first.Journey is { SourceSteps: 200, RecordedSteps: 200 }, "Raw steps did not advance journey");
Check(first.Journey.ActiveItem is null, "An unused item appeared active");
Check(await QuestSteps() == 200, "Raw steps did not advance quest");
await items.UseShoesAsync(user, default);
Check(await Quantity(shoesId) == 1, "Shoes were not consumed once");
var equipped = await explore.LoadAsync(user, default);
Check(equipped.Journey?.ActiveItem is { ItemType: "SPEED_SHOES", Multiplier: 2, Range: 5000, RemainingSteps: 5000 }, "Equipped shoes indicator is missing");
var boosted = await service.SyncStepsAsync(user, new(trip, 400), default);
Check(boosted.Journey is { SourceSteps: 400, RecordedSteps: 600 }, "Shoes should boost only journey, not raw steps");
Check(boosted.Journey.ActiveItem is { RemainingSteps: 4800 }, "Item indicator must count raw steps remaining");
Check(await QuestSteps() == 400, "Quest counted boosted steps instead of raw steps");
await quests.LoadAsync(user, default);
var rewarded = await Quantity(shoesId);
Check(rewarded >= 3, "Completed quest did not award items");
await service.SyncStepsAsync(user, new(trip, 400), default);
await quests.LoadAsync(user, default);
Check(await QuestSteps() == 400 && await Quantity(shoesId) == rewarded, "Duplicate sync repeated progress/reward");
try { await items.UseFlightAsync(user, from, default); throw new Exception("Flight allowed during journey"); }
catch (ItemUseException error) when (error.Code == "JOURNEY_ACTIVE") { }
Check(await Quantity(ticketId) == 2, "Rejected flight consumed a ticket");
var completed = await service.SyncStepsAsync(user, new(trip, 800), default);
Check(completed.Journey is { SourceSteps: 800, RecordedSteps: 1000, Status: "COMPLETED" }, "Journey did not complete with raw steps and shoes bonus");
Check(completed.Journey.ActiveItem is null, "Completed journey still shows active shoes");
Check((long)(await Sql("SELECT nearest_place_id FROM virtual_location WHERE user_id=$1", user))! == to, "Completion did not move virtual location");
Check((long)(await Sql("SELECT count(*) FROM user_discovered_place WHERE user_id=$1 AND place_id=$2", user, to))! == 1, "Completion did not unlock place exactly once");
await service.SyncStepsAsync(user, new(trip, 800), default);
Check(await QuestSteps() == 800, "Completion retry counted steps again");
var landing = (long)(await Sql("SELECT place_id FROM place WHERE status='ACTIVE' AND region_id<>(SELECT region_id FROM place WHERE place_id=$1) ORDER BY place_id LIMIT 1", to))!;
await items.UseFlightAsync(user, landing, default);
Check(await Quantity(ticketId) == 1, "Flight did not consume exactly one ticket");
Check((long)(await Sql("SELECT nearest_place_id FROM virtual_location WHERE user_id=$1", user))! == landing, "Flight did not move virtual location");
try { await service.SyncStepsAsync(user + 10_000_000, new(trip, 900), default); throw new Exception("Another user could sync the journey"); }
catch (JourneyException error) when (error.Code == "JOURNEY_NOT_FOUND") { }
Console.WriteLine("PASS: cumulative steps, raw/boosted separation, quest progress/reward once, shoes consumption, duplicate requests, journey completion/unlock, flight restrictions/consumption, ownership.");

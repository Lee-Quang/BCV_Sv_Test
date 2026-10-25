using Bcv.Api.Modules.Explore.Application;
using Bcv.Api.Modules.Quest;
using Bcv.Api.Modules.Inventory;
using Bcv.Api.Modules.Profile;
using Npgsql;
using NpgsqlTypes;

namespace Bcv.Api.Modules.Explore.Infrastructure;

public sealed class PostgresExploreRepository(NpgsqlDataSource db) : IExploreRepository
{
    private const string DistanceSql = """
        ST_Distance(
            ST_SetSRID(ST_MakePoint(v.longitude::double precision, v.latitude::double precision), 4326)::geography,
            ST_SetSRID(ST_MakePoint(p.longitude::double precision, p.latitude::double precision), 4326)::geography)
        """;

    public async Task<ExploreSnapshot> LoadAsync(long userId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        return await LoadSnapshotAsync(connection, userId, ct);
    }

    public async Task<ExplorePlaceDetail> LoadPlaceAsync(long userId, long placeId, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        long id;
        string name, placeType, regionName;
        string? introduction, highlights;
        bool discovered;
        decimal distanceKm;
        await using (var command = new NpgsqlCommand($"""
            SELECT p.place_id, p.name, pt.name,
                   EXISTS (SELECT 1 FROM public.user_discovered_place d
                           WHERE d.user_id = $1 AND d.place_id = p.place_id),
                   ({DistanceSql} / 1000.0)::numeric, p.introduction, p.highlights, r.name
            FROM public.place p
            JOIN public.place_type pt ON pt.place_type_id = p.place_type_id
            JOIN public.region r ON r.region_id = p.region_id
            JOIN public.virtual_location v ON v.user_id = $1
            WHERE p.place_id = $2
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = placeId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
                throw new ExploreException("PLACE_NOT_FOUND", "Không tìm thấy địa danh hoặc vị trí ảo.");
            id = reader.GetInt64(0);
            name = reader.GetString(1);
            placeType = reader.GetString(2);
            discovered = reader.GetBoolean(3);
            distanceKm = reader.GetDecimal(4);
            introduction = discovered && !reader.IsDBNull(5) ? reader.GetString(5) : null;
            highlights = discovered && !reader.IsDBNull(6) ? reader.GetString(6) : null;
            regionName = reader.GetString(7);
        }

        var images = new List<string>();
        await using (var command = new NpgsqlCommand("""
            SELECT image_key FROM public.place_image
            WHERE place_id = $1 ORDER BY place_image_id
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = placeId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct)) images.Add(reader.GetString(0));
        }

        // The first photo stays visible (blurred) while locked; the gallery opens with the place.
        return new ExplorePlaceDetail(id, name, placeType, images.FirstOrDefault(), discovered, distanceKm,
            distanceKm <= 0 ? 0 : RequiredSteps(distanceKm), introduction, highlights, regionName,
            discovered ? images : []);
    }

    public async Task<ExploreSnapshot> CreateJourneyAsync(long userId, CreateJourneyCommand request,
        CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);
        // A user's virtual-location row serializes competing journey creation requests.
        long? startPlaceId;
        decimal startLatitude;
        decimal startLongitude;
        await using (var command = new NpgsqlCommand("""
            SELECT nearest_place_id, latitude, longitude FROM public.virtual_location
            WHERE user_id = $1 FOR UPDATE
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
                throw new ExploreException("NO_STARTING_POINT", "Hãy chọn điểm xuất phát trước.");
            startPlaceId = reader.IsDBNull(0) ? null : reader.GetInt64(0);
            startLatitude = reader.GetDecimal(1);
            startLongitude = reader.GetDecimal(2);
        }

        long? activeJourneyId = null;
        long? activePlaceId = null;
        await using (var command = new NpgsqlCommand("""
            SELECT journey_id, destination_place_id FROM public.journey
            WHERE user_id = $1 AND status = 'IN_PROGRESS' FOR UPDATE
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (await reader.ReadAsync(ct))
            {
                activeJourneyId = reader.GetInt64(0);
                activePlaceId = reader.GetInt64(1);
            }
        }
        if (activePlaceId == request.PlaceId)
        {
            await transaction.CommitAsync(ct);
            return await LoadSnapshotAsync(connection, userId, ct);
        }
        if (activeJourneyId is not null && !request.ReplaceActive)
            throw new ExploreException("ACTIVE_JOURNEY_EXISTS", "Bạn đang có hành trình khác. Xác nhận để thay điểm đến.");

        decimal distanceKm;
        await using (var command = new NpgsqlCommand($"""
            SELECT ({DistanceSql} / 1000.0)::numeric
            FROM public.place p JOIN public.virtual_location v ON v.user_id = $1
            WHERE p.place_id = $2 AND p.status <> 'INACTIVE'
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = request.PlaceId, NpgsqlDbType = NpgsqlDbType.Bigint });
            var value = await command.ExecuteScalarAsync(ct);
            if (value is not decimal distance || distance <= 0)
                throw new ExploreException("INVALID_DESTINATION", "Điểm đến không khả dụng hoặc trùng vị trí ảo.");
            distanceKm = distance;
        }

        if (activeJourneyId is not null)
        {
            await using var abandon = new NpgsqlCommand("""
                UPDATE public.journey SET status = 'ABANDONED' WHERE journey_id = $1
                """, connection, transaction);
            abandon.Parameters.Add(new NpgsqlParameter<long> { TypedValue = activeJourneyId.Value, NpgsqlDbType = NpgsqlDbType.Bigint });
            await abandon.ExecuteNonQueryAsync(ct);
        }
        await using (var insert = new NpgsqlCommand("""
            INSERT INTO public.journey (user_id, start_place_id, start_latitude, start_longitude,
                destination_place_id, distance, required_steps, status)
            VALUES ($1, $2, $3, $4, $5, $6, $7, 'IN_PROGRESS')
            """, connection, transaction))
        {
            insert.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            insert.Parameters.Add(new NpgsqlParameter<long?> { TypedValue = startPlaceId, NpgsqlDbType = NpgsqlDbType.Bigint });
            insert.Parameters.Add(new NpgsqlParameter<decimal> { TypedValue = startLatitude, NpgsqlDbType = NpgsqlDbType.Numeric });
            insert.Parameters.Add(new NpgsqlParameter<decimal> { TypedValue = startLongitude, NpgsqlDbType = NpgsqlDbType.Numeric });
            insert.Parameters.Add(new NpgsqlParameter<long> { TypedValue = request.PlaceId, NpgsqlDbType = NpgsqlDbType.Bigint });
            insert.Parameters.Add(new NpgsqlParameter<decimal> { TypedValue = Math.Round(distanceKm, 2), NpgsqlDbType = NpgsqlDbType.Numeric });
            insert.Parameters.Add(new NpgsqlParameter<int> { TypedValue = RequiredSteps(distanceKm), NpgsqlDbType = NpgsqlDbType.Integer });
            await insert.ExecuteNonQueryAsync(ct);
        }
        await transaction.CommitAsync(ct);
        return await LoadSnapshotAsync(connection, userId, ct);
    }

    private static int RequiredSteps(decimal distanceKm) =>
        (int)Math.Clamp(Math.Ceiling(distanceKm * 1000m / 0.75m), 1m, int.MaxValue);

    public async Task<ExploreSnapshot> SyncStepsAsync(long userId, SyncStepsCommand request, CancellationToken ct)
    {
        await using var connection = await db.OpenConnectionAsync(ct);
        await using var transaction = await connection.BeginTransactionAsync(ct);
        DateTime startedAt;
        int recordedSteps;
        int requiredSteps;
        long destinationPlaceId;
        string status;
        await using (var command = new NpgsqlCommand("""
            SELECT started_at, recorded_steps, required_steps, destination_place_id, status
            FROM public.journey
            WHERE journey_id = $1 AND user_id = $2
            FOR UPDATE
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = request.JourneyId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
                throw new ExploreException("JOURNEY_NOT_FOUND", "Không tìm thấy hành trình của bạn.");
            startedAt = reader.GetDateTime(0);
            recordedSteps = reader.GetInt32(1);
            requiredSteps = reader.GetInt32(2);
            destinationPlaceId = reader.GetInt64(3);
            status = reader.GetString(4);
        }

        if (status != "IN_PROGRESS")
        {
            await transaction.CommitAsync(ct);
            return await LoadSnapshotAsync(connection, userId, ct);
        }

        var sourceId = $"health-connect:journey:{request.JourneyId}";
        var previousSteps = 0;
        await using (var command = new NpgsqlCommand("""
            SELECT step_count FROM public.step_data
            WHERE user_id = $1 AND source_data_id = $2
            FOR UPDATE
            """, connection, transaction))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = sourceId, NpgsqlDbType = NpgsqlDbType.Varchar });
            var value = await command.ExecuteScalarAsync(ct);
            if (value is int count) previousSteps = count;
        }

        var cumulativeSteps = checked((int)request.CumulativeSteps);
        if (cumulativeSteps > previousSteps)
        {
            await using (var command = new NpgsqlCommand("""
                INSERT INTO public.step_data
                    (user_id, source_data_id, step_count, start_time, end_time)
                VALUES ($1, $2, $3, $4, GREATEST(CURRENT_TIMESTAMP, $4))
                ON CONFLICT (user_id, source_data_id) DO UPDATE
                    SET step_count = EXCLUDED.step_count,
                        end_time = EXCLUDED.end_time,
                        synced_at = CURRENT_TIMESTAMP
                """, connection, transaction))
            {
                command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
                command.Parameters.Add(new NpgsqlParameter<string> { TypedValue = sourceId, NpgsqlDbType = NpgsqlDbType.Varchar });
                command.Parameters.Add(new NpgsqlParameter<int> { TypedValue = cumulativeSteps, NpgsqlDbType = NpgsqlDbType.Integer });
                command.Parameters.Add(new NpgsqlParameter<DateTime> { TypedValue = startedAt, NpgsqlDbType = NpgsqlDbType.Timestamp });
                await command.ExecuteNonQueryAsync(ct);
            }

            // Only the steps new since the last sync count toward today's and this week's quests.
            await QuestProgress.RecordAsync(connection, transaction, userId, QuestProgress.StepCount,
                cumulativeSteps - previousSteps, ct);

            // Raw steps count once; an active speed-shoe boost adds its extra share to this journey only.
            var boosts = await SpeedShoes.LoadBoostsAsync(connection, transaction, userId, request.JourneyId, ct);
            var bonus = SpeedShoes.Bonus(boosts, previousSteps, cumulativeSteps);
            var newRecorded = (int)Math.Min(requiredSteps, (long)recordedSteps + cumulativeSteps - previousSteps + bonus);
            var completed = newRecorded >= requiredSteps;
            await using (var command = new NpgsqlCommand("""
                UPDATE public.journey
                SET recorded_steps = $1,
                    status = CASE WHEN $2 THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
                    completed_at = CASE WHEN $2 THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE journey_id = $3
                """, connection, transaction))
            {
                command.Parameters.Add(new NpgsqlParameter<int> { TypedValue = newRecorded, NpgsqlDbType = NpgsqlDbType.Integer });
                command.Parameters.Add(new NpgsqlParameter<bool> { TypedValue = completed, NpgsqlDbType = NpgsqlDbType.Boolean });
                command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = request.JourneyId, NpgsqlDbType = NpgsqlDbType.Bigint });
                await command.ExecuteNonQueryAsync(ct);
            }

            if (completed)
            {
                await using (var command = new NpgsqlCommand("""
                    UPDATE public.virtual_location v
                    SET region_id = p.region_id, nearest_place_id = p.place_id,
                        latitude = p.latitude, longitude = p.longitude,
                        updated_at = CURRENT_TIMESTAMP
                    FROM public.place p
                    WHERE v.user_id = $1 AND p.place_id = $2
                    """, connection, transaction))
                {
                    command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
                    command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = destinationPlaceId, NpgsqlDbType = NpgsqlDbType.Bigint });
                    await command.ExecuteNonQueryAsync(ct);
                }
                await PlaceUnlock.UnlockAsync(connection, transaction, userId, destinationPlaceId, ct);

                await using (var name = new NpgsqlCommand("SELECT name FROM public.place WHERE place_id = $1",
                    connection, transaction))
                {
                    name.Parameters.Add(new NpgsqlParameter<long> { TypedValue = destinationPlaceId, NpgsqlDbType = NpgsqlDbType.Bigint });
                    var placeName = (string?)await name.ExecuteScalarAsync(ct) ?? "điểm đến";
                    await Notifications.AddAsync(connection, transaction, userId, Notifications.JourneyCompleted,
                        "Hành trình hoàn thành", $"Bạn đã đến {placeName}.", ct);
                }

                await QuestProgress.RecordAsync(connection, transaction, userId, QuestProgress.CompleteJourney, 1, ct);
            }
        }

        await transaction.CommitAsync(ct);
        return await LoadSnapshotAsync(connection, userId, ct);
    }

    private static async Task<ExploreSnapshot> LoadSnapshotAsync(NpgsqlConnection connection,
        long userId, CancellationToken ct)
    {
        ExploreLocation? location = null;
        await using (var command = new NpgsqlCommand("""
            SELECT r.name, p.name, v.latitude, v.longitude
            FROM public.virtual_location v
            JOIN public.region r ON r.region_id = v.region_id
            LEFT JOIN public.place p ON p.place_id = v.nearest_place_id
            WHERE v.user_id = $1
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (await reader.ReadAsync(ct))
                location = new ExploreLocation(reader.GetString(0), reader.IsDBNull(1) ? null : reader.GetString(1),
                    reader.GetDecimal(2), reader.GetDecimal(3));
        }

        ExploreJourney? journey = null;
        await using (var command = new NpgsqlCommand("""
            SELECT j.journey_id, p.name, p.latitude, p.longitude,
                   j.required_steps, j.recorded_steps, j.status,
                   EXTRACT(EPOCH FROM j.started_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::bigint,
                   j.start_latitude, j.start_longitude
            FROM public.journey j
            JOIN public.place p ON p.place_id = j.destination_place_id
            WHERE j.user_id = $1 AND j.status IN ('IN_PROGRESS', 'COMPLETED')
            ORDER BY CASE WHEN j.status = 'IN_PROGRESS' THEN 0 ELSE 1 END,
                     j.started_at DESC, j.journey_id DESC
            LIMIT 1
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            if (await reader.ReadAsync(ct))
                journey = new ExploreJourney(reader.GetInt64(0), reader.GetString(1),
                    reader.GetDecimal(2), reader.GetDecimal(3), reader.GetInt32(4),
                    reader.GetInt32(5), reader.GetString(6), reader.GetInt64(7),
                    reader.GetDecimal(8), reader.GetDecimal(9));
        }
        var places = new List<ExploreMapPlace>();
        await using (var command = new NpgsqlCommand("""
            SELECT p.place_id, p.name, p.latitude, p.longitude,
                   (d.place_id IS NOT NULL) AS is_discovered
            FROM public.place p
            LEFT JOIN public.user_discovered_place d
              ON d.place_id = p.place_id AND d.user_id = $1
            WHERE p.status <> 'INACTIVE'
            ORDER BY p.place_id
            """, connection))
        {
            command.Parameters.Add(new NpgsqlParameter<long> { TypedValue = userId, NpgsqlDbType = NpgsqlDbType.Bigint });
            await using var reader = await command.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                places.Add(new ExploreMapPlace(reader.GetInt64(0), reader.GetString(1),
                    reader.GetDecimal(2), reader.GetDecimal(3), reader.GetBoolean(4)));
        }
        return new ExploreSnapshot(location, journey, places);
    }
}

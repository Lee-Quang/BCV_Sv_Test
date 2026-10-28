using System.Text.Json;
using Bcv.Api.Modules.Discovery.Domain;
using Npgsql;

namespace Bcv.Api.Modules.Admin;

internal static class AdminPlaceFeatures
{
    // Same configuration order as the recommendation reader. Save everything in
    // the place transaction so incomplete model data cannot become visible.
    public static async Task Save(NpgsqlConnection connection, NpgsqlTransaction transaction,
        long placeId, JsonElement body, CancellationToken ct)
    {
        long configId;
        int clusterCount;
        await using (var config = new NpgsqlCommand("""
            SELECT recommendation_config_id, cluster_count FROM public.recommendation_config
            ORDER BY (status = 'ACTIVE') DESC, updated_at DESC, recommendation_config_id DESC
            LIMIT 1 FOR SHARE
            """, connection, transaction))
        await using (var reader = await config.ExecuteReaderAsync(ct))
        {
            if (!await reader.ReadAsync(ct)) throw new AdminValidation("Chưa có cấu hình gợi ý. Hãy tạo cấu hình trước khi lưu địa danh.");
            configId = reader.GetInt64(0);
            clusterCount = reader.GetInt32(1);
        }
        var selected = new HashSet<string>(StringComparer.Ordinal);
        var provided = body.TryGetProperty("feature_codes", out var array);
        if (provided)
        {
            if (array.ValueKind != JsonValueKind.Array || array.GetArrayLength() > 35)
                throw new AdminValidation("Danh sách đặc trưng địa danh không hợp lệ.");
            foreach (var item in array.EnumerateArray())
            {
                if (item.ValueKind != JsonValueKind.String || !PlaceFeatureClassifier.Features.Contains(item.GetString()!))
                    throw new AdminValidation("Đặc trưng địa danh không được hỗ trợ.");
                selected.Add(item.GetString()!);
            }
        }
        else
        {
            await using var existing = new NpgsqlCommand("""
                SELECT c.code FROM public.place_feature pf
                JOIN public.place_feature_value fv ON fv.place_feature_id=pf.place_feature_id
                JOIN public.recommendation_criterion c ON c.criterion_id=fv.criterion_id
                WHERE pf.place_id=$1 AND pf.recommendation_config_id=$2 AND fv.feature_value=1
                """, connection, transaction);
            existing.Parameters.AddWithValue(placeId); existing.Parameters.AddWithValue(configId);
            await using var reader = await existing.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct)) selected.Add(reader.GetString(0));
        }
        if (selected.Count == 0) throw new AdminValidation("Hãy chọn ít nhất một đặc trưng nội dung để phân cụm địa danh.");
        if (clusterCount != 15) throw new AdminValidation("Mô hình hiện tại hỗ trợ 15 cụm. Cần cập nhật mô hình cho cấu hình này.");
        // Do not silently create a partial feature vector if reference data is missing.
        await using (var criteria = new NpgsqlCommand("SELECT count(*) FROM public.recommendation_criterion WHERE code=ANY($1)", connection, transaction))
        {
            criteria.Parameters.AddWithValue(PlaceFeatureClassifier.Features.ToArray());
            if ((long)(await criteria.ExecuteScalarAsync(ct))! != PlaceFeatureClassifier.Features.Count)
                throw new AdminValidation("Thiếu dữ liệu 35 đặc trưng mô hình trong cơ sở dữ liệu.");
        }
        long featureId;
        await using (var upsert = new NpgsqlCommand("""
            INSERT INTO public.place_feature(place_id,recommendation_config_id,cluster_number)
            VALUES($1,$2,$3) ON CONFLICT(place_id,recommendation_config_id)
            DO UPDATE SET cluster_number=EXCLUDED.cluster_number,updated_at=CURRENT_TIMESTAMP
            RETURNING place_feature_id
            """, connection, transaction))
        {
            upsert.Parameters.AddWithValue(placeId); upsert.Parameters.AddWithValue(configId);
            upsert.Parameters.AddWithValue(PlaceFeatureClassifier.Classify(selected));
            featureId = (long)(await upsert.ExecuteScalarAsync(ct))!;
        }
        await using var values = new NpgsqlCommand("""
            INSERT INTO public.place_feature_value(place_feature_id,criterion_id,feature_value)
            SELECT $1,criterion_id,CASE WHEN code=ANY($2) THEN 1.0 ELSE 0.0 END
            FROM public.recommendation_criterion WHERE code=ANY($3)
            ON CONFLICT(place_feature_id,criterion_id) DO UPDATE SET feature_value=EXCLUDED.feature_value
            """, connection, transaction);
        values.Parameters.AddWithValue(featureId); values.Parameters.AddWithValue(selected.ToArray());
        values.Parameters.AddWithValue(PlaceFeatureClassifier.Features.ToArray());
        await values.ExecuteNonQueryAsync(ct);
    }
}

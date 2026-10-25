using System.Globalization;
using System.Security.Cryptography;
using System.Text;

namespace Bcv.Api.Modules.Profile;

/// <summary>Where avatar images live. Returns the value stored in APP_USER.AVATAR.</summary>
public interface IAvatarStorage
{
    Task<string> SaveAsync(long userId, byte[] jpeg, CancellationToken ct);

    /// <summary>Best effort: removes an avatar this storage created earlier; anything else is ignored.</summary>
    Task DeleteAsync(string? stored, CancellationToken ct);
}

/// <summary>Without cloud storage configured, the image itself is kept in the column as a data URI.</summary>
public sealed class InlineAvatarStorage : IAvatarStorage
{
    public Task<string> SaveAsync(long userId, byte[] jpeg, CancellationToken ct) =>
        Task.FromResult("data:image/jpeg;base64," + Convert.ToBase64String(jpeg));

    public Task DeleteAsync(string? stored, CancellationToken ct) => Task.CompletedTask;
}

public sealed record R2Settings(string AccountId, string Bucket, string AccessKeyId, string SecretAccessKey,
    string PublicBaseUrl);

/// <summary>
/// Cloudflare R2 through its S3-compatible API, signed with AWS Signature V4 (region "auto").
/// Objects are public through <see cref="R2Settings.PublicBaseUrl"/> (an r2.dev or custom domain),
/// so only that URL is stored and the keys never leave the server.
/// </summary>
public sealed class R2AvatarStorage(R2Settings settings, HttpClient http, ILogger<R2AvatarStorage> log) : IAvatarStorage
{
    private const string EmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private string Host => $"{settings.AccountId}.r2.cloudflarestorage.com";
    private string PublicBase => settings.PublicBaseUrl.TrimEnd('/');

    public async Task<string> SaveAsync(long userId, byte[] jpeg, CancellationToken ct)
    {
        // A new key per upload, so phones never show a cached older picture under the same URL.
        var key = $"avatars/{userId}/{Guid.NewGuid():N}.jpg";
        using var request = Signed(HttpMethod.Put, key, jpeg);
        request.Content = new ByteArrayContent(jpeg);
        request.Content.Headers.ContentType = new("image/jpeg");
        using var response = await http.SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            var body = await response.Content.ReadAsStringAsync(ct);
            throw new InvalidOperationException($"R2 upload failed: {(int)response.StatusCode} {body}");
        }
        return $"{PublicBase}/{key}";
    }

    public async Task DeleteAsync(string? stored, CancellationToken ct)
    {
        if (stored is null || !stored.StartsWith(PublicBase + "/avatars/", StringComparison.Ordinal)) return;
        try
        {
            using var request = Signed(HttpMethod.Delete, stored[(PublicBase.Length + 1)..], null);
            using var response = await http.SendAsync(request, ct);
            if (!response.IsSuccessStatusCode)
                log.LogWarning("Old avatar not deleted from R2: {Status}", (int)response.StatusCode);
        }
        catch (Exception ex) { log.LogWarning(ex, "Old avatar not deleted from R2"); }
    }

    private HttpRequestMessage Signed(HttpMethod method, string key, byte[]? payload)
    {
        var now = DateTime.UtcNow;
        var amzDate = now.ToString("yyyyMMdd'T'HHmmss'Z'", CultureInfo.InvariantCulture);
        var date = now.ToString("yyyyMMdd", CultureInfo.InvariantCulture);
        var payloadHash = payload is null ? EmptyHash : Hex(SHA256.HashData(payload));
        var path = "/" + Uri.EscapeDataString(settings.Bucket) + "/" +
                   string.Join('/', key.Split('/').Select(Uri.EscapeDataString));

        var canonical = string.Join('\n', method.Method, path, "",
            $"host:{Host}", $"x-amz-content-sha256:{payloadHash}", $"x-amz-date:{amzDate}", "",
            "host;x-amz-content-sha256;x-amz-date", payloadHash);
        var scope = $"{date}/auto/s3/aws4_request";
        var toSign = string.Join('\n', "AWS4-HMAC-SHA256", amzDate, scope,
            Hex(SHA256.HashData(Encoding.UTF8.GetBytes(canonical))));
        var signingKey = new[] { date, "auto", "s3", "aws4_request" }
            .Aggregate(Encoding.UTF8.GetBytes("AWS4" + settings.SecretAccessKey), (k, part) => Hmac(k, part));
        var signature = Hex(Hmac(signingKey, toSign));

        var request = new HttpRequestMessage(method, $"https://{Host}{path}");
        request.Headers.TryAddWithoutValidation("x-amz-date", amzDate);
        request.Headers.TryAddWithoutValidation("x-amz-content-sha256", payloadHash);
        request.Headers.TryAddWithoutValidation("Authorization",
            $"AWS4-HMAC-SHA256 Credential={settings.AccessKeyId}/{scope}, " +
            $"SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={signature}");
        return request;
    }

    private static byte[] Hmac(byte[] key, string data) => HMACSHA256.HashData(key, Encoding.UTF8.GetBytes(data));
    private static string Hex(byte[] bytes) => Convert.ToHexString(bytes).ToLowerInvariant();
}

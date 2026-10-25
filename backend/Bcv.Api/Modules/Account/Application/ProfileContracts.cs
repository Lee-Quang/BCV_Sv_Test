namespace Bcv.Api.Modules.Account.Application;

/// <summary>Avatar is the image URL (Cloudflare R2), or a JPEG data URI when no cloud storage is set; null when none.</summary>
public sealed record AccountProfile(string FullName, string Email, string? Avatar);

/// <summary>Avatar is a new JPEG as a data URI; omitted (null) keeps the current one.</summary>
public sealed record UpdateProfileRequest(string? FullName, string? Avatar);

public sealed class ProfileException(string code, string message, string? field = null) : Exception(message)
{
    public string Code { get; } = code;
    public string? Field { get; } = field;
}

public interface IProfileRepository
{
    Task<AccountProfile?> FindAsync(long userId, CancellationToken ct);
    Task UpdateAsync(long userId, string fullName, string? avatar, CancellationToken ct);
    Task CloseAccountAsync(long userId, CancellationToken ct);
}

/// <summary>Where avatar images live. Returns the value stored in APP_USER.AVATAR.</summary>
public interface IAvatarStorage
{
    Task<string> SaveAsync(long userId, byte[] jpeg, CancellationToken ct);

    /// <summary>Best effort: removes an avatar this storage created earlier; anything else is ignored.</summary>
    Task DeleteAsync(string? stored, CancellationToken ct);
}

/// <summary>Profile rules: name and avatar checks, storing the avatar, and closing the account.</summary>
public sealed class ProfileService(IProfileRepository repository, IAvatarStorage avatars, ISessionStore sessions)
{
    private const string JpegDataUri = "data:image/jpeg;base64,";

    public async Task<AccountProfile> LoadAsync(long userId, CancellationToken ct) =>
        await repository.FindAsync(userId, ct) ?? throw new ProfileException("NOT_FOUND", "Không tìm thấy tài khoản.");

    public async Task<AccountProfile> UpdateAsync(long userId, UpdateProfileRequest request, CancellationToken ct)
    {
        var name = string.Join(' ', (request.FullName ?? "").Split(' ', StringSplitOptions.RemoveEmptyEntries));
        if (name.Length is < 2 or > 100)
            throw new ProfileException("INVALID_NAME", "Họ và tên cần từ 2 đến 100 ký tự.", "fullName");
        byte[]? jpeg = null;
        if (request.Avatar is { } avatar)
        {
            try { jpeg = avatar.StartsWith(JpegDataUri, StringComparison.Ordinal) ? Convert.FromBase64String(avatar[JpegDataUri.Length..]) : null; }
            catch (FormatException) { jpeg = null; }
            if (jpeg is not { Length: > 2 and <= 300_000 } || jpeg[0] != 0xFF || jpeg[1] != 0xD8)
                throw new ProfileException("INVALID_AVATAR", "Ảnh đại diện không hợp lệ hoặc quá lớn.", "avatar");
        }

        var previous = (await LoadAsync(userId, ct)).Avatar;
        var stored = jpeg is null ? null : await avatars.SaveAsync(userId, jpeg, ct);
        await repository.UpdateAsync(userId, name, stored, ct);
        if (stored is not null) await avatars.DeleteAsync(previous, ct);
        return await LoadAsync(userId, ct);
    }

    /// <summary>Closes the account (status DELETED) and ends its sessions; login refuses accounts that are not ACTIVE.</summary>
    public async Task CloseAccountAsync(long userId, CancellationToken ct)
    {
        await repository.CloseAccountAsync(userId, ct);
        sessions.RevokeAll(userId);
    }
}

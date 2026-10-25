namespace Bcv.Api.Modules.Account.Api;

public sealed record RegisterRequest(string? Email, string? Password, string? ConfirmPassword);
public sealed record LoginRequest(string? Email, string? Password);
public sealed record PasswordResetRequest(string? Email);
public sealed record PasswordResetVerifyRequest(string? Email, string? Code);
public sealed record PasswordResetCompleteRequest(string? ResetToken, string? Password, string? ConfirmPassword);
public sealed record ChangePasswordRequest(string? CurrentPassword, string? NewPassword, string? ConfirmPassword);

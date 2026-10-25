namespace Bcv.Api.Modules.Shared.Api;

/// <summary>Error body shared by every endpoint; Field names the input it concerns, when any.</summary>
public sealed record ApiError(string Code, string Message, string? Field = null);

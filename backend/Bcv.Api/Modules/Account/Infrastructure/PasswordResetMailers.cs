using System.Net;
using System.Net.Mail;
using Bcv.Api.Modules.Account.Application;

namespace Bcv.Api.Modules.Account.Infrastructure;

public sealed record SmtpSettings(string Host, int Port, string? User, string? Password, string From, bool EnableSsl);

/// <summary>Sends the code by email. Configured through the Smtp__* settings.</summary>
public sealed class SmtpPasswordResetMailer(SmtpSettings settings) : IPasswordResetMailer
{
    public async Task SendCodeAsync(string email, string code, CancellationToken cancellationToken)
    {
        using var message = new MailMessage(settings.From, email)
        {
            Subject = "Mã xác minh Bước Chân Việt",
            Body = $"Mã xác minh khôi phục mật khẩu của bạn là: {code}\n\n" +
                $"Mã có hiệu lực trong {PasswordResetService.CodeLifetimeSeconds / 60} phút. " +
                "Nếu bạn không yêu cầu, hãy bỏ qua email này."
        };
        using var client = new SmtpClient(settings.Host, settings.Port) { EnableSsl = settings.EnableSsl };
        if (!string.IsNullOrEmpty(settings.User))
            client.Credentials = new NetworkCredential(settings.User, settings.Password);
        await client.SendMailAsync(message, cancellationToken);
    }
}

/// <summary>Development fallback when no SMTP server is configured: the code is written to the API log.</summary>
public sealed class LogPasswordResetMailer(ILogger<LogPasswordResetMailer> logger) : IPasswordResetMailer
{
    public Task SendCodeAsync(string email, string code, CancellationToken cancellationToken)
    {
        logger.LogWarning("Password reset code for {Email}: {Code} (SMTP not configured)", email, code);
        return Task.CompletedTask;
    }
}

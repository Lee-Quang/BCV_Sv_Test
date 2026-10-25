using System.Security.Cryptography;

namespace Bcv.Api.Modules.Account.Domain;

public static class PasswordHasher
{
    private const int Iterations = 210_000;
    private const int SaltBytes = 16;
    private const int HashBytes = 32;

    public static string Hash(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltBytes);
        var hash = Rfc2898DeriveBytes.Pbkdf2(password, salt, Iterations, HashAlgorithmName.SHA256, HashBytes);
        return $"pbkdf2-sha256${Iterations}${Convert.ToBase64String(salt)}${Convert.ToBase64String(hash)}";
    }

    public static bool Verify(string password, string stored)
    {
        try
        {
            var parts = stored.Split('$');
            if (parts.Length != 4 || parts[0] != "pbkdf2-sha256" ||
                !int.TryParse(parts[1], out var iterations) || iterations < 100_000 || iterations > 1_000_000)
                return false;
            var salt = Convert.FromBase64String(parts[2]);
            var expected = Convert.FromBase64String(parts[3]);
            if (salt.Length != SaltBytes || expected.Length != HashBytes) return false;
            var actual = Rfc2898DeriveBytes.Pbkdf2(password, salt, iterations, HashAlgorithmName.SHA256, expected.Length);
            return CryptographicOperations.FixedTimeEquals(actual, expected);
        }
        catch (FormatException)
        {
            return false;
        }
    }
}

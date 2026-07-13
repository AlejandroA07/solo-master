using Microsoft.AspNetCore.Mvc.Testing;

namespace SoloMaster.IntegrationTests;

public sealed class HomePageTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly HttpClient _client;

    public HomePageTests(WebApplicationFactory<Program> application)
    {
        _client = application.CreateClient(
            new WebApplicationFactoryClientOptions
            {
                AllowAutoRedirect = false,
                BaseAddress = new Uri("https://localhost"),
            });
    }

    [Fact]
    public async Task HomePageReturnsSoloMasterShell()
    {
        using var response = await _client.GetAsync("/", TestContext.Current.CancellationToken);
        var content = await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken);

        Assert.True(response.IsSuccessStatusCode);
        Assert.Contains("data-testid=\"home-heading\"", content, StringComparison.Ordinal);
        Assert.Contains("Solo Master", content, StringComparison.Ordinal);
    }
}

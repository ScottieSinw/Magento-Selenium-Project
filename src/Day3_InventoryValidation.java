import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class Day3_InventoryValidation {

    private static void acceptAnySecurityAlert(WebDriver driver) {
        try {
            // Some environments show a confirm dialog about insecure form submission.
            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(2));
            Alert alert = shortWait.until(ExpectedConditions.alertIsPresent());
            System.out.println("INFO: Alert detected -> " + alert.getText());
            alert.accept();
            System.out.println("INFO: Alert accepted.");
        } catch (TimeoutException ignored) {
            // No alert appeared; continue.
        }
    }

    public static void main(String[] args) {
        WebDriver driver = new FirefoxDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // Open TechPanda demo site
            driver.get("https://live.techpanda.org/index.php/");

            // Click the MOBILE menu
            wait.until(ExpectedConditions.elementToBeClickable(By.linkText("MOBILE"))).click();

            // Click the first product on the mobile listing page
            wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("h2.product-name a")
            )).click();

            // Enter an exaggerated quantity to trigger inventory validation
            WebElement qty = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("input.qty")));
            qty.clear();
            qty.sendKeys("999");

            // Add to cart
            WebElement addToCart = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.btn-cart")));
            addToCart.click();

            // TechPanda may show a security confirmation dialog (insecure connection warning)
            acceptAnySecurityAlert(driver);

            // Validate error message (different pages may render slightly differently)
            WebElement errorMsg = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li.error-msg, .error-msg, .error")
            ));
            String actual = errorMsg.getText().trim();
            System.out.println("ERROR MESSAGE: " + actual);
            String lower = actual.toLowerCase();
            if (lower.contains("requested") || lower.contains("available") || lower.contains("not") || lower.contains("maximum") || lower.contains("qty") || lower.contains("quantity")) {
                System.out.println("✅ PASS: Inventory validation message displayed.");
            } else {
                throw new AssertionError("❌ FAIL: Unexpected error message: " + actual);
            }
        } finally {
            driver.quit();
        }
    }
}
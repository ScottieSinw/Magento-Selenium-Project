import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;

public class EcommerceTest {
    public static void main(String[] args) {
        System.setProperty("webdriver.gecko.driver", "geckodriver");

        WebDriver driver = new FirefoxDriver();
        driver.get("https://live.techpanda.org/index.php/");

        try { Thread.sleep(5000); } catch (Exception e) {}

        driver.quit();
    }
}
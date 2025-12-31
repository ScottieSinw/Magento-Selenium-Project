import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class Day4_CompareProductsPopup {

    private static void safeClick(WebDriverWait wait, WebElement el) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(el)).click();
                return;
            } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                attempts++;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
            }
        }
        wait.until(ExpectedConditions.elementToBeClickable(el)).click();
    }

    private static int getCompareCount(WebDriver driver) {
        // TechPanda compare block sometimes exposes the count as text (more stable than counting <li>). Try text first.
        List<By> countTextSelectors = Arrays.asList(
                By.cssSelector("#block-compare .block-title strong span"),
                By.cssSelector(".block-compare .block-title strong span"),
                By.cssSelector("#block-compare .block-title"),
                By.cssSelector(".block-compare .block-title")
        );

        for (By sel : countTextSelectors) {
            try {
                String t = driver.findElement(sel).getText();
                if (t != null) {
                    String digits = t.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        return Integer.parseInt(digits);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback: count items in the compare block.
        List<By> itemSelectors = Arrays.asList(
                By.cssSelector("#block-compare li.item"),
                By.cssSelector(".block-compare li.item"),
                By.cssSelector("#compare-items li"),
                By.cssSelector("#block-compare .block-content li"),
                By.cssSelector(".block-compare .block-content li")
        );

        int max = 0;
        for (By sel : itemSelectors) {
            try {
                int n = driver.findElements(sel).size();
                if (n > max) max = n;
            } catch (Exception ignored) {
            }
        }
        return max;
    }

    private static void jsClick(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", el);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private static void addOneProductToCompare(WebDriver driver, WebDriverWait wait) {
        // Always re-locate cards (DOM can refresh after each compare click)
        By cards = By.cssSelector("ul.products-grid li.item");
        wait.until(ExpectedConditions.visibilityOfElementLocated(cards));

        int before = getCompareCount(driver);

        // Re-fetch cards each iteration to avoid stale references
        for (int i = 0; i < driver.findElements(cards).size(); i++) {
            try {
                List<WebElement> freshCards = driver.findElements(cards);
                if (i >= freshCards.size()) break;

                WebElement card = freshCards.get(i);

                String name = "(unknown)";
                try {
                    name = card.findElement(By.cssSelector("h2.product-name a")).getText().trim();
                } catch (Exception ignored) {}

                // Find the *Add to Compare* link inside this card.
                WebElement compareLink = null;

                // Most common (TechPanda)
                List<WebElement> byClass = card.findElements(By.cssSelector("a.link-compare"));
                if (!byClass.isEmpty()) {
                    compareLink = byClass.get(0);
                }

                // Fallback by visible text
                if (compareLink == null) {
                    List<WebElement> byText = card.findElements(By.xpath(
                            ".//a[contains(normalize-space(.),'Add to Compare') or contains(normalize-space(.),'Compare')]"));
                    if (!byText.isEmpty()) {
                        compareLink = byText.get(0);
                    }
                }

                if (compareLink == null) {
                    System.out.println("WARN: Compare link not found for product index " + i + ". Trying next product...");
                    continue;
                }

                // Click the link. Prefer normal click (since it can trigger navigation); fall back to JS.
                try {
                    safeClick(wait, compareLink);
                } catch (Exception clickFail) {
                    jsClick(driver, compareLink);
                }

                // Wait for either a success message OR compare count to increase.
                // This site can refresh the page after adding to compare.
                wait.until(d -> {
                    if (getCompareCount(d) > before) return true;
                    return !d.findElements(By.cssSelector("li.success-msg, .success-msg")).isEmpty();
                });

                int after = getCompareCount(driver);
                if (after <= before) {
                    // Sometimes the message appears but count parsing fails—print diagnostics and try next.
                    String url = "";
                    try { url = driver.getCurrentUrl(); } catch (Exception ignored) {}
                    System.out.println("WARN: Compare count did not increase after clicking '" + name + "'. before=" + before + ", after=" + after + ", url=" + url);
                    continue;
                }

                System.out.println("INFO: Added to compare -> " + name + " (compare count " + before + " -> " + after + ")");

                // Ensure we're still on the MOBILE listing page before the next add.
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".category-products")));
                return;

            } catch (StaleElementReferenceException se) {
                System.out.println("WARN: Stale element while adding product index " + i + ". Retrying...");
                i--; // retry same index
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            } catch (org.openqa.selenium.TimeoutException te) {
                System.out.println("WARN: Timed out while adding product index " + i + ". Trying next...");

            } catch (Exception e) {
                System.out.println("WARN: Unexpected issue for product index " + i + ": " + e.getClass().getSimpleName());
            }
        }

        throw new RuntimeException("Unable to add a new product to Compare. Current compare count=" + before);
    }

    private static String waitForPopup(WebDriver driver, Set<String> beforeHandles) {
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(d -> d.getWindowHandles().size() > beforeHandles.size());

        Set<String> after = new HashSet<>(driver.getWindowHandles());
        after.removeAll(beforeHandles);
        if (after.isEmpty()) {
            throw new RuntimeException("Popup window not found");
        }
        return after.iterator().next();
    }

    public static void main(String[] args) {
        WebDriver driver = new FirefoxDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get("https://live.techpanda.org/index.php/");

            // 1) MOBILE 이동
            wait.until(ExpectedConditions.elementToBeClickable(By.linkText("MOBILE"))).click();
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".category-products")));

            // 2) Fill Compare with 2 distinct products (count-based, with guardrail)
            int guard = 0;
            while (getCompareCount(driver) < 2 && guard < 10) {
                addOneProductToCompare(driver, wait);
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".category-products")));
                guard++;
            }
            if (getCompareCount(driver) < 2) {
                throw new RuntimeException(
                        "Could not add 2 distinct products to Compare. Final compare count=" + getCompareCount(driver));
            }

            // Compare button opens a popup via onclick="popWin(...)". Target that attribute for stability.
            WebElement compareBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("button[title='Compare'][onclick*='popWin']")
            ));

            // Snapshot window handles BEFORE clicking (critical — otherwise the wait condition can never be satisfied)
            String mainWindow = driver.getWindowHandle();
            Set<String> beforeHandles = new HashSet<>(driver.getWindowHandles());
            int beforeCount = beforeHandles.size();

            System.out.println("DEBUG: before click, compareCount=" + getCompareCount(driver));
            System.out.println("DEBUG: windows before click=" + beforeCount);
            System.out.println("DEBUG: clicking Compare button now...");

            try {
                safeClick(wait, compareBtn);
            } catch (Exception clickFail) {
                jsClick(driver, compareBtn);
            }

            // Ensure a new window opened
            wait.until(d -> d.getWindowHandles().size() > beforeCount);

            // 4) Popup 전환
            String popup = waitForPopup(driver, beforeHandles);
            driver.switchTo().window(popup);
            System.out.println("✅ PASS: Compare popup window opened.");

            // 5) 비교 테이블 검증
            wait.until(ExpectedConditions
                    .visibilityOfElementLocated(By.cssSelector("#product_comparison, .data-table.compare-table")));
            System.out.println("✅ PASS: Comparison table is visible in popup.");

            // 6) 닫고 원래 창으로
            driver.close();
            driver.switchTo().window(mainWindow);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".category-products")));
            System.out.println("✅ PASS: Returned to main page.");

        } finally {
            driver.quit();
        }
    }
}
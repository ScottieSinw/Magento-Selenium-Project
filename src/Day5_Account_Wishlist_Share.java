import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.Wait;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public class Day5_Account_Wishlist_Share {

    // ===== Config =====
    private static final String BASE_URL = "http://live.techpanda.org/index.php/";
    private static final Duration WAIT = Duration.ofSeconds(8);
    private static final Duration FAST_WAIT = Duration.ofSeconds(2);
    private static final Duration ALERT_WAIT = Duration.ofSeconds(1);

    // ===== Helpers =====
    private static void jsClick(WebDriver driver, WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", el);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private static void acceptAlertIfPresent(WebDriver driver) {
        // Fast path: most of the time there is no alert. Avoid an unconditional wait.
        for (int i = 0; i < 3; i++) {
            try {
                Alert a = driver.switchTo().alert();
                System.out.println("INFO: Alert detected -> " + a.getText());
                a.accept();
                System.out.println("INFO: Alert accepted.");
                return;
            } catch (NoAlertPresentException ignored) {
                try { Thread.sleep(120); } catch (InterruptedException ignored2) {}
            } catch (UnhandledAlertException uae) {
                // If Selenium reports an unhandled alert, try to accept it.
                try {
                    Alert a = driver.switchTo().alert();
                    System.out.println("INFO: Alert detected -> " + a.getText());
                    a.accept();
                    System.out.println("INFO: Alert accepted.");
                    return;
                } catch (Exception ignored) {
                    // fall through
                }
            }
        }
        // Last resort: a short explicit wait
        try {
            Alert a = new WebDriverWait(driver, ALERT_WAIT).until(ExpectedConditions.alertIsPresent());
            System.out.println("INFO: Alert detected -> " + a.getText());
            a.accept();
            System.out.println("INFO: Alert accepted.");
        } catch (TimeoutException ignored) {
        }
    }

    private static void normalizeUrl(WebDriver driver) {
        // We intentionally run TechPanda on HTTP end-to-end to avoid HTTPS->HTTP confirm dialogs.
        // Keep this helper as a placeholder for future normalization if needed.
    }

    private static String randomEmail() {
        return "qa_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@mail.com";
    }

    private static void assertLoggedIn(WebDriver driver, WebDriverWait wait) {
        WebElement welcome = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".welcome-msg, p.welcome-msg")
        ));
        String t = welcome.getText().toLowerCase(Locale.ROOT);
        if (!t.contains("welcome")) {
            throw new AssertionError("FAIL: Welcome message not found. Actual=" + welcome.getText());
        }
        System.out.println("✅ PASS: Logged-in state confirmed -> " + welcome.getText().trim());
    }

    private static void openAccountMenu(WebDriver driver, WebDriverWait wait) {
        acceptAlertIfPresent(driver);

        WebElement accountTop = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("a.skip-account, .account-cart-wrapper a.skip-account")
        ));
        jsClick(driver, accountTop);

        // Wait for dropdown to render (helps with Logout / Login / Register)
        new WebDriverWait(driver, FAST_WAIT)
                .until(ExpectedConditions.or(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("#header-account")),
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(".skip-content.skip-active"))
                ));
    }

    private static WebElement findClickable(WebDriver driver, Duration perTry, By... locators) {
        Wait<WebDriver> w = new WebDriverWait(driver, perTry);

        for (By loc : locators) {
            try {
                return w.until(ExpectedConditions.elementToBeClickable(loc));
            } catch (TimeoutException ignored) {
                // try next
            }
        }
        return null;
    }

    // ===== Step 1: Create Account =====
    private static AccountData createAccount(WebDriver driver, WebDriverWait wait) {
        driver.get(BASE_URL);
        acceptAlertIfPresent(driver);

        openAccountMenu(driver, wait);

        WebElement register = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Register")));
        jsClick(driver, register);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.account-create, .page-title")));

        String firstName = "QA";
        String lastName = "Champion";
        String email = randomEmail();
        String password = "Qatest!12345";

        wait.until(ExpectedConditions.elementToBeClickable(By.id("firstname"))).sendKeys(firstName);
        driver.findElement(By.id("lastname")).sendKeys(lastName);
        driver.findElement(By.id("email_address")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.id("confirmation")).sendKeys(password);

        WebElement registerBtn = driver.findElement(By.cssSelector("button[title='Register']"));
        jsClick(driver, registerBtn);
        acceptAlertIfPresent(driver);

        assertLoggedIn(driver, wait);

        System.out.println("INFO: Account created -> " + email);
        return new AccountData(firstName, lastName, email, password);
    }

    // ===== Step 1-2: Logout =====
    private static void logout(WebDriver driver, WebDriverWait wait) {
        System.out.println("INFO: Attempting logout. url=" + driver.getCurrentUrl());

        // TechPanda sometimes renders Log Out only inside the Account dropdown.
        // We'll try multiple robust locators, then fall back to direct logout URL.
        openAccountMenu(driver, wait);

        WebElement logoutLink = findClickable(driver, FAST_WAIT,
                // Common Magento1 patterns
                By.cssSelector("a[title='Log Out']"),
                By.cssSelector("a[href*='customer/account/logout']"),
                By.cssSelector("a[href*='account/logout']"),
                By.xpath("//a[contains(normalize-space(.),'Log Out')]"),
                By.xpath("//a[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'log out')]")
        );

        if (logoutLink != null) {
            jsClick(driver, logoutLink);
            acceptAlertIfPresent(driver);
            // After logout, Magento typically redirects to home and shows account links again
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a.skip-account, .account-cart-wrapper a.skip-account")),
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("body"))
            ));
            System.out.println("✅ PASS: Logged out via UI link.");
            return;
        }

        // Fallback: direct logout endpoint (most reliable)
        System.out.println("WARN: 'Log Out' link not found/clickable. Falling back to direct logout URL.");
        driver.get(BASE_URL + "customer/account/logout/");
        acceptAlertIfPresent(driver);
        wait.until(ExpectedConditions.or(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector("a.skip-account, .account-cart-wrapper a.skip-account")),
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector("body"))
        ));
        System.out.println("✅ PASS: Logged out via direct URL.");
    }

    // ===== Step 1-3: Login with created account =====
    private static void login(WebDriver driver, WebDriverWait wait, AccountData acc) {
        driver.get(BASE_URL);
        acceptAlertIfPresent(driver);

        openAccountMenu(driver, wait);

        WebElement login = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Log In")));
        jsClick(driver, login);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email")));

        driver.findElement(By.id("email")).sendKeys(acc.email);
        driver.findElement(By.id("pass")).sendKeys(acc.password);

        WebElement loginBtn = driver.findElement(By.id("send2"));
        jsClick(driver, loginBtn);
        acceptAlertIfPresent(driver);

        assertLoggedIn(driver, wait);
        System.out.println("✅ PASS: Re-login successful -> " + acc.email);
    }

    // ===== Step 2: Add product to Wishlist from category page =====
    private static void addOneWishlistFromCategory(WebDriver driver, WebDriverWait wait, String menuLinkText, AccountData acc) {
        // menuLinkText: "MOBILE" or "TV"
        // Strategy: navigate by direct URL (fast + robust), then add first product to wishlist.
        // If Magento redirects us to login after clicking wishlist, we auto re-login and retry once.

        String categoryUrl;
        if ("MOBILE".equalsIgnoreCase(menuLinkText)) {
            categoryUrl = BASE_URL + "mobile.html";
        } else if ("TV".equalsIgnoreCase(menuLinkText)) {
            categoryUrl = BASE_URL + "tv.html";
        } else {
            // fallback to clicking top menu
            categoryUrl = null;
        }

        int attempts = 0;
        while (attempts < 2) {
            attempts++;

            // 1) Ensure we are on the category page
            if (categoryUrl != null) {
                driver.get(categoryUrl);
                normalizeUrl(driver);
            } else {
                WebElement menu = wait.until(ExpectedConditions.elementToBeClickable(By.linkText(menuLinkText)));
                jsClick(driver, menu);
                normalizeUrl(driver);
            }

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".category-products")));

            // 2) First product card
            WebElement firstItem = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("ul.products-grid li.item")
            ));

            String productName = "(unknown)";
            try {
                productName = firstItem.findElement(By.cssSelector("h2.product-name a")).getText().trim();
            } catch (Exception ignored) {}

            // 3) Add to Wishlist (click link, not navigate)
            WebElement addWishlist = firstItem.findElement(By.cssSelector("a.link-wishlist"));
            jsClick(driver, addWishlist);
            acceptAlertIfPresent(driver);

            // 4) Wait for outcome: wishlist page OR success msg OR login page
            boolean ok = new WebDriverWait(driver, FAST_WAIT)
                    .until(d -> {
                        String url = d.getCurrentUrl().toLowerCase(Locale.ROOT);
                        if (url.contains("wishlist")) return true;
                        if (!d.findElements(By.cssSelector("#wishlist-view-form")).isEmpty()) return true;
                        if (!d.findElements(By.cssSelector(".my-wishlist, .wishlist-view")).isEmpty()) return true;
                        if (!d.findElements(By.cssSelector("li.success-msg, .success-msg")).isEmpty()) return true;
                        if (url.contains("customer/account/login")) return true;
                        return false;
                    });

            if (!ok) {
                throw new TimeoutException("No visible result after clicking Add to Wishlist");
            }

            // 5) If redirected to login, re-login and retry once
            String currentUrl = driver.getCurrentUrl().toLowerCase(Locale.ROOT);
            boolean onLogin = currentUrl.contains("customer/account/login");

            if (onLogin) {
                System.out.println("WARN: Redirected to login after wishlist click. Re-logging in and retrying... (attempt " + attempts + ")");
                login(driver, wait, acc);
                // retry loop
                continue;
            }

            // 6) Success logging
            boolean onWishlistPage = driver.getCurrentUrl().toLowerCase(Locale.ROOT).contains("wishlist")
                    || !driver.findElements(By.cssSelector("#wishlist-view-form")).isEmpty();

            if (onWishlistPage) {
                System.out.println("✅ PASS: Added to wishlist from " + menuLinkText.toUpperCase(Locale.ROOT) + " -> " + productName + " (wishlist page)");
            } else {
                String msg = "(success msg not found)";
                try {
                    msg = driver.findElement(By.cssSelector("li.success-msg, .success-msg")).getText().trim();
                } catch (Exception ignored) {}
                System.out.println("✅ PASS: Added to wishlist from " + menuLinkText.toUpperCase(Locale.ROOT) + " -> " + productName + " (msg=" + msg + ")");
            }

            return; // done
        }

        throw new RuntimeException("Unable to add wishlist item from " + menuLinkText + " after retries.");
    }

    private static void openMyWishlist(WebDriver driver, WebDriverWait wait) {
        // 위시리스트 공유/검증은 'My Wishlist' 페이지에서 해야 안정적
        openAccountMenu(driver, wait);

        // Magento1 TechPanda에서 일반적으로 "My Wishlist" 링크 제공
        WebElement wishlistLink = findClickable(driver, FAST_WAIT,
                By.linkText("My Wishlist"),
                By.cssSelector("a[href*='wishlist/index/index']"),
                By.cssSelector("a[href*='wishlist']"),
                By.xpath("//a[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'wishlist')]")
        );

        if (wishlistLink != null) {
            jsClick(driver, wishlistLink);
        } else {
            // fallback direct URL
            driver.get(BASE_URL + "wishlist/");
            acceptAlertIfPresent(driver);
        }

        acceptAlertIfPresent(driver);

        // Wishlist page markers
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("#wishlist-view-form")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector(".my-wishlist, .wishlist-view")),
                ExpectedConditions.urlContains("wishlist")
        ));

        System.out.println("✅ PASS: Opened My Wishlist page. url=" + driver.getCurrentUrl());
    }

    // ===== Step 3: Share Wishlist via Email =====
    private static void shareWishlist(WebDriver driver, WebDriverWait wait, String shareEmail) {
        // Wishlist page에서 "Share Wishlist" 버튼 클릭
        WebElement shareBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button[title='Share Wishlist'], button[title='Share Wishlist'] span span, .buttons-set button[title='Share Wishlist']")
        ));
        jsClick(driver, shareBtn);

        // Share form email field can vary by theme; try id first then fallback
        By emailField = By.id("email_address");
        if (driver.findElements(emailField).isEmpty()) {
            emailField = By.cssSelector("#email_address, textarea[name='email_address'], textarea#email");
        }
        wait.until(ExpectedConditions.visibilityOfElementLocated(emailField));

        WebElement emailBox = driver.findElement(emailField);
        emailBox.clear();
        emailBox.sendKeys(shareEmail);

        // 메시지는 선택이지만 UX를 위해 입력
        try {
            WebElement msg = driver.findElement(By.id("message"));
            msg.clear();
            msg.sendKeys("Hi! Sharing my wishlist items with you.");
        } catch (NoSuchElementException ignored) {}

        WebElement submit = driver.findElement(By.cssSelector("button[title='Share Wishlist']"));
        jsClick(driver, submit);

        acceptAlertIfPresent(driver);

        // 성공 메시지 검증
        WebElement success = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("li.success-msg, .success-msg")
        ));
        System.out.println("✅ PASS: Wishlist shared. Message -> " + success.getText().trim());
    }

    // ===== Data Holder =====
    private static class AccountData {
        String firstName, lastName, email, password;
        AccountData(String firstName, String lastName, String email, String password) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.password = password;
        }
    }

    public static void main(String[] args) {
        FirefoxOptions options = new FirefoxOptions();
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        WebDriver driver = new FirefoxDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(20));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(20));
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        try {
            // 1-1 Create account (unique email)
            AccountData acc = createAccount(driver, wait);
            normalizeUrl(driver);
            System.out.println("✅ STEP 1-1 COMPLETE: Account created.");

            // 1-2 Logout
            logout(driver, wait);
            System.out.println("✅ STEP 1-2 COMPLETE: Logged out.");

            // 1-3 Login with created account
            login(driver, wait, acc);
            normalizeUrl(driver);
            System.out.println("✅ STEP 1-3 COMPLETE: Logged in again.");

            // 2-1 Add one product to wishlist from MOBILE
            addOneWishlistFromCategory(driver, wait, "MOBILE", acc);
            System.out.println("✅ STEP 2-1 COMPLETE: Wishlist added from MOBILE.");

            // 2-2 Add one product to wishlist from TV
            addOneWishlistFromCategory(driver, wait, "TV", acc);
            System.out.println("✅ STEP 2-2 COMPLETE: Wishlist added from TV.");

            // Ensure we are on the Wishlist page before sharing
            openMyWishlist(driver, wait);

            // 3-1 Share wishlist (use your own email here)
            String shareTo = "qa.receiver@mail.com"; // 원하는 이메일로 바꿔도 됨
            shareWishlist(driver, wait, shareTo);
            System.out.println("✅ STEP 3-1 COMPLETE: Wishlist shared via email.");

            System.out.println("🎯 DAY 5 DONE: Account + Logout/Login + Wishlist + Share validated end-to-end.");

        } finally {
            driver.quit();
        }
    }
}
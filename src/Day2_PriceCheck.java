import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class Day2_PriceCheck {

    static final String BASE_URL = "https://live.techpanda.org/index.php/";
    static final List<String> PRODUCT_NAMES = Arrays.asList(
            "SONY XPERIA",
            "IPHONE",
            "SAMSUNG GALAXY"
    );

    public static void main(String[] args) {
        WebDriver driver = new FirefoxDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        Map<String, String> listPrices = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();

        try {
            // 1) MOBILE 이동 후 리스트 가격 수집
            openMobile(driver, wait);

            for (String name : PRODUCT_NAMES) {
                try {
                    String listPriceRaw = getListPrice(driver, wait, name);
                    listPrices.put(name, normalizePrice(listPriceRaw));
                } catch (Exception e) {
                    issues.add(name + " => LIST_ERROR(" + e.getClass().getSimpleName() + ")");
                    listPrices.put(name, null);
                }
            }

            // 2) 3개를 순서대로: 클릭 -> 상세가격 읽기 -> 비교
            for (String name : PRODUCT_NAMES) {
                System.out.println("\n=== START: " + name + " ===");

                try {
                    openMobile(driver, wait); // 항상 MOBILE에서 시작
                    openProductDetail(driver, wait, name);

                    // 상세 페이지 가격: 할인(special) 우선 처리
                    String detailPriceRaw = getDetailPrice(driver, wait);

                    String listNorm = listPrices.get(name);
                    String detailNorm = normalizePrice(detailPriceRaw);

                    System.out.println("List   : " + listNorm);
                    System.out.println("Detail : " + detailNorm);

                    if (listNorm == null) {
                        issues.add(name + " => LIST_PRICE_MISSING");
                    } else if (!Objects.equals(listNorm, detailNorm)) {
                        issues.add(name + " => MISMATCH(List " + listNorm + " vs Detail " + detailNorm + ")");
                    }

                } catch (Exception e) {
                    issues.add(name + " => DETAIL_ERROR(" + e.getClass().getSimpleName() + ")");
                }

                System.out.println("=== END: " + name + " ===");
            }

            // 3) 리포트
            if (issues.isEmpty()) {
                System.out.println("\n✅ PASS: All product prices match (list vs detail).");
            } else {
                System.out.println("\n❌ FAIL: Issues found:");
                for (String s : issues) System.out.println(" - " + s);
            }

        } finally {
            driver.quit();
        }
    }

    private static void openMobile(WebDriver driver, WebDriverWait wait) {
        driver.get(BASE_URL);
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("MOBILE"))).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".category-products")));
    }

    // 텍스트 완전일치 대신: 대소문자 무시 contains로 상품 링크를 찾는다.
    private static By productLinkLocator(String productNameUpper) {
        return By.xpath("//h2[@class='product-name']/a[contains(translate(normalize-space(.), " +
                "'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'), '" + productNameUpper + "')]");
    }

    private static String getListPrice(WebDriver driver, WebDriverWait wait, String productName) {
        By link = productLinkLocator(productName);

        WebElement productLink = wait.until(ExpectedConditions.visibilityOfElementLocated(link));
        WebElement item = productLink.findElement(By.xpath("./ancestor::li[contains(@class,'item')]"));

        // 1) special-price가 있으면 그걸 우선
        List<WebElement> special = item.findElements(By.cssSelector(".special-price .price"));
        if (!special.isEmpty()) return special.get(0).getText();

        // 2) 없으면 regular price
        return item.findElement(By.cssSelector(".price-box .price")).getText();
    }

    private static void openProductDetail(WebDriver driver, WebDriverWait wait, String productName) {
        By link = productLinkLocator(productName);
        wait.until(ExpectedConditions.elementToBeClickable(link)).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".product-view")));
    }

    // 상세 페이지 가격도 "할인 가격 우선"으로 방어
    private static String getDetailPrice(WebDriver driver, WebDriverWait wait) {
        WebElement shop = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector(".product-shop"))
        );

        List<WebElement> special = shop.findElements(By.cssSelector(".special-price .price"));
        if (!special.isEmpty()) return special.get(0).getText();

        return shop.findElement(By.cssSelector(".price-box .price")).getText();
    }

    private static String normalizePrice(String raw) {
        if (raw == null) return null;
        return raw.replaceAll("[^0-9.]", "").trim();
    }
}
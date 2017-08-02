package hr.dstimac.dominance

import org.junit.{Ignore, Test}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver

class WebDriverQuitTest {

  @Test
  @Ignore
  def bar(): Unit = {
    val cfg = ApplicationConfig.apply()
    sys.props("webdriver.gecko.driver") = cfg.geckoDriverLocation
    val d: WebDriver = new FirefoxDriver()
    d.get("https://www.google.com")
    Thread.sleep(2000)

    d.quit() // seems like bug in ff/gecko
  }
}

/*
 * The MIT License
 *
 * Copyright 2021 Daniel Beck
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.bugs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.endsWithIgnoringCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import hudson.model.FreeStyleProject;
import hudson.security.Messages;
import hudson.security.Permission;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.xml.sax.SAXException;

@RunWith(Parameterized.class)
public class Jenkins64991Test {
    @Parameterized.Parameters
    public static List<String> contexts() {
        return Arrays.asList("/jenkins", "");
    }

    public Jenkins64991Test(String context) {
        j.contextPath = context;
    }

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Permission.READ).everywhere().toEveryone().grant(Jenkins.ADMINISTER).everywhere().to("alice"));
        j.createFreeStyleProject("foo bar");
    }

    @Test
    public void test403Redirect() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage loginPage = webClient.goTo("manage");

        assertTrue(loginPage.isHtmlPage());
        assertThat(loginPage.getUrl().toExternalForm(), containsStringIgnoringCase("%2Fmanage"));

        ((HtmlTextInput) loginPage.getElementByName("j_username")).setText("alice");
        ((HtmlPasswordInput) loginPage.getElementByName("j_password")).setText("alice");

        final Page redirectedPage = HtmlFormUtil.submit(loginPage.getFormByName("login"));
        assertTrue(redirectedPage.isHtmlPage());
        assertEquals(j.getURL() + "manage/", redirectedPage.getUrl().toExternalForm());
        assertThat(redirectedPage.getWebResponse().getContentAsString(), containsStringIgnoringCase(Messages.GlobalSecurityConfiguration_DisplayName()));
    }


    @Test
    public void testRedirect() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage indexPage = webClient.goTo("");

        final Page loginPage = indexPage.getElementsByTagName("a").stream().filter(
                e -> e.hasAttribute("href") && e.getAttribute("href").contains(j.jenkins.getSecurityRealm().getLoginUrl())
        ).findFirst().orElseThrow(() -> new RuntimeException("cannot find login link")).click();
        // Could be simplified to `indexPage.getElementById("login-link").click();` if we're willing to edit loginLink.jelly

        assertTrue(loginPage.isHtmlPage());
        assertThat(loginPage.getUrl().toExternalForm(), endsWithIgnoringCase("%2F"));

        HtmlPage loginHtmlPage = (HtmlPage) loginPage;
        ((HtmlTextInput) loginHtmlPage.getElementByName("j_username")).setText("alice");
        ((HtmlPasswordInput) loginHtmlPage.getElementByName("j_password")).setText("alice");

        final Page redirectedPage = HtmlFormUtil.submit(loginHtmlPage.getFormByName("login"));
        assertTrue(redirectedPage.isHtmlPage());
        assertEquals(indexPage.getUrl(), redirectedPage.getUrl());
    }

    @Test
    public void withoutFrom() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage loginPage = webClient.goTo("login");

        assertTrue(loginPage.isHtmlPage());

        ((HtmlTextInput) loginPage.getElementByName("j_username")).setText("alice");
        ((HtmlPasswordInput) loginPage.getElementByName("j_password")).setText("alice");

        final Page redirectedPage = HtmlFormUtil.submit(loginPage.getFormByName("login"));
        assertTrue(redirectedPage.isHtmlPage());
        assertEquals(j.getURL(), redirectedPage.getUrl());
    }

    @Test
    public void emptyFrom() throws Exception {
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage loginPage = webClient.goTo("login?from=");

        assertTrue(loginPage.isHtmlPage());

        ((HtmlTextInput) loginPage.getElementByName("j_username")).setText("alice");
        ((HtmlPasswordInput) loginPage.getElementByName("j_password")).setText("alice");

        final Page redirectedPage = HtmlFormUtil.submit(loginPage.getFormByName("login"));
        assertTrue(redirectedPage.isHtmlPage());
        assertEquals(j.getURL(), redirectedPage.getUrl());
    }

    @Test
    public void testRedirectToProject() throws Exception {
        FreeStyleProject freeStyleProject = j.jenkins.getItemByFullName("foo bar", FreeStyleProject.class);
        assertNotNull(freeStyleProject);
        final JenkinsRule.WebClient webClient = j.createWebClient();
        final HtmlPage projectPage = webClient.getPage(freeStyleProject);
        assertThat(projectPage.getWebResponse().getContentAsString(), containsStringIgnoringCase("Project " + freeStyleProject.getDisplayName()));

        final Page loginPage = projectPage.getElementsByTagName("a").stream().filter(
                e -> e.hasAttribute("href") && e.getAttribute("href").contains(j.jenkins.getSecurityRealm().getLoginUrl())
        ).findFirst().orElseThrow(() -> new RuntimeException("cannot find login link")).click();
        // Could be simplified to `projectPage.getElementById("login-link").click();` if we're willing to edit loginLink.jelly

        assertTrue(loginPage.isHtmlPage());
        assertThat(loginPage.getUrl().toExternalForm(), endsWithIgnoringCase("%2Fjob%2Ffoo%2520bar%2F"));

        HtmlPage loginHtmlPage = (HtmlPage) loginPage;
        ((HtmlTextInput) loginHtmlPage.getElementByName("j_username")).setText("alice");
        ((HtmlPasswordInput) loginHtmlPage.getElementByName("j_password")).setText("alice");

        final Page redirectedPage = HtmlFormUtil.submit(loginHtmlPage.getFormByName("login"));
        assertTrue(redirectedPage.isHtmlPage());
        assertEquals(freeStyleProject.getAbsoluteUrl(), redirectedPage.getUrl().toExternalForm());
    }

    @Test
    public void absoluteRedirect() throws Exception {
        assertNoOpenRedirect("login?from=https:%2F%2Fjenkins.io");
    }

    @Test
    public void protocolRelativeRedirect() throws Exception {
        String loginUrl = "login?from=%2F%2Fjenkins.io";
        assertNoOpenRedirect(loginUrl);
    }

    @Test
    public void hostRelativeRedirect() throws Exception {
        String loginUrl = "login?from=%2Fjenkins.io";
        assertNoOpenRedirect(loginUrl);
    }

    @Test
    public void relativeRedirect() throws Exception {
        String loginUrl = "login?from=jenkins.io";
        assertNoOpenRedirect(loginUrl);
    }

    private void assertNoOpenRedirect(String loginUrl) throws IOException, SAXException {
        final JenkinsRule.WebClient webClient = j.createWebClient().withThrowExceptionOnFailingStatusCode(false);
        final HtmlPage loginPage = webClient.goTo(loginUrl);

        assertTrue(loginPage.isHtmlPage());
        ((HtmlTextInput) loginPage.getElementById("j_username")).setText("alice");
        ((HtmlPasswordInput) loginPage.getElementByName("j_password")).setText("alice");
        final Page redirectedPage = HtmlFormUtil.submit(loginPage.getFormByName("login"));

        assertTrue(redirectedPage.isHtmlPage());

        // We don't really care where this ends up as long as it doesn't leave the host
        // TODO Do we need to ensure we remain in the context path?
        assertEquals(j.getURL().getHost(), redirectedPage.getUrl().getHost());
    }
}

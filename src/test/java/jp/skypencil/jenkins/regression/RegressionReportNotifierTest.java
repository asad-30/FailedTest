package jp.skypencil.jenkins.regression;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.CaseResult.Status;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.AggregatedTestResultAction;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.PackageResult;
import hudson.tasks.junit.TestResult;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import javax.mail.Address;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.Part;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.collect.Lists;

import hudson.FilePath;
import java.net.URL;
import java.io.File;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;


@RunWith(PowerMockRunner.class)
@PrepareForTest(CaseResult.class)
public class RegressionReportNotifierTest {
    private BuildListener listener;
    private Launcher launcher;
    private AbstractBuild<?, ?> build, build2;

    @Before
    public void setUp() throws Exception {
        listener = mock(BuildListener.class);
        launcher = mock(Launcher.class);
        build = mock(AbstractBuild.class);
        build2 = mock(AbstractBuild.class);
        PrintStream logger = mock(PrintStream.class);
        doReturn("").when(build).getUrl();
        doReturn(logger).when(listener).getLogger();
    }

    @Test
    public void testCompileErrorOccured() throws InterruptedException,
            IOException {
        doReturn(null).when(build).getAction(AbstractTestResultAction.class);
        RegressionReportNotifier notifier = new RegressionReportNotifier("", false, false, true, true, false, false);

        assertThat(notifier.perform(build, launcher, listener), is(true));
    }

    @Test
    public void testSend() throws InterruptedException, MessagingException {
        makeRegression();

        RegressionReportNotifier notifier = new RegressionReportNotifier("author@mail.com", false, false, true, true, false, false);
        MockedMailSender mailSender = new MockedMailSender();
        notifier.setMailSender(mailSender);

        assertThat(notifier.perform(build, launcher, listener), is(true));
        assertThat(mailSender.getSentMessage(), is(notNullValue()));
        Address[] to = mailSender.getSentMessage().getRecipients(RecipientType.TO);
        assertThat(to.length, is(1));
        assertThat(to[0].toString(), is(equalTo("author@mail.com")));
    }

    @Test
    public void testSendToCulprits() throws InterruptedException, MessagingException {
        makeRegression();

        RegressionReportNotifier notifier = new RegressionReportNotifier("author@mail.com", true, false, true, true, false, false);
        MockedMailSender mailSender = new MockedMailSender();
        notifier.setMailSender(mailSender);

        assertThat(notifier.perform(build, launcher, listener), is(true));
        assertThat(mailSender.getSentMessage(), is(notNullValue()));
        Address[] to = mailSender.getSentMessage().getRecipients(RecipientType.TO);
        assertThat(to.length, is(2));
        assertThat(to[0].toString(), is(equalTo("author@mail.com")));
        assertThat(to[1].toString(), is(equalTo("culprit@mail.com")));
    }

    @Test
    public void testAttachLogFile() throws InterruptedException, MessagingException, IOException {
        makeRegression();
        
        Writer writer = null;
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("log"), "utf-8"));
        writer.write("regression");
        writer.close();

        File f = new File("log");
        doReturn(f).when(build).getLogFile();

        RegressionReportNotifier notifier = new RegressionReportNotifier("author@mail.com", false, true, true, true, false, false);
        MockedMailSender mailSender = new MockedMailSender();
        notifier.setMailSender(mailSender);

        assertThat(build.getLogFile(), is(notNullValue()));
        assertThat(notifier.perform(build, launcher, listener), is(true)); 
        assertThat(mailSender.getSentMessage(), is(notNullValue()));

        assertThat(notifier.getAttachLogs(), is(true));
        assertThat(mailSender.getSentMessage().getContent() instanceof Multipart, is(true));
        
        Multipart multipartContent = (Multipart) mailSender.getSentMessage().getContent();
        assertThat(multipartContent.getCount(), is(2));
        assertThat(((MimeBodyPart)multipartContent.getBodyPart(1)).getDisposition(), is(equalTo(Part.ATTACHMENT)));
        assertThat(((MimeBodyPart)multipartContent.getBodyPart(0)).getDisposition(), is(nullValue()));

        f.delete();
    }

    @Test
    public void testAttachLogFile2() throws InterruptedException, MessagingException, IOException {
        makeRegression();
        
        Writer writer = null;
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("log"), "utf-8"));
        writer.write("test");
        writer.close();

        File f = new File("log");
        doReturn(f).when(build).getLogFile();

        RegressionReportNotifier notifier = new RegressionReportNotifier("author@mail.com", true, true, true, true, false, false);
        MockedMailSender mailSender = new MockedMailSender();
        notifier.setMailSender(mailSender);

        assertThat(build.getLogFile(), is(notNullValue()));
        assertThat(notifier.perform(build, launcher, listener), is(true)); 
        assertThat(mailSender.getSentMessage(), is(notNullValue()));

        Address[] to = mailSender.getSentMessage().getRecipients(RecipientType.TO);
        assertThat(to.length, is(2));
        assertThat(to[0].toString(), is(equalTo("author@mail.com")));
        assertThat(to[1].toString(), is(equalTo("culprit@mail.com")));

        assertThat(notifier.getAttachLogs(), is(true));
        assertThat(mailSender.getSentMessage().getContent() instanceof Multipart, is(true));
        
        Multipart multipartContent = (Multipart) mailSender.getSentMessage().getContent();
        assertThat(multipartContent.getCount(), is(2));
        assertThat(((MimeBodyPart)multipartContent.getBodyPart(1)).getDisposition(), is(equalTo(Part.ATTACHMENT)));
        assertThat(((MimeBodyPart)multipartContent.getBodyPart(0)).getDisposition(), is(nullValue()));

        f.delete();
    }


    private void makeRegression() {
        AbstractTestResultAction<?> result = mock(AbstractTestResultAction.class);
        doReturn(result).when(build).getAction(AbstractTestResultAction.class);
        doReturn(Result.FAILURE).when(build).getResult();
        User culprit = mock(User.class);
        doReturn("culprit").when(culprit).getId();
        doReturn(new ChangeLogSetMock(build).withChangeBy(culprit)).when(build).getChangeSet();

        CaseResult failedTest = mock(CaseResult.class);
        doReturn(Status.REGRESSION).when(failedTest).getStatus();
        List<CaseResult> failedTests = Lists.newArrayList(failedTest);
        doReturn(failedTests).when(result).getFailedTests();
    }


    private static final class MockedMailSender implements
            RegressionReportNotifier.MailSender {
        private MimeMessage sentMessage = null;

        @Override
        public void send(MimeMessage message) throws MessagingException {
            sentMessage = message;
        }

        public MimeMessage getSentMessage() {
            return sentMessage;
        }
    }
}

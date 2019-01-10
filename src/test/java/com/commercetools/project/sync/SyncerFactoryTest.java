package com.commercetools.project.sync;

import static com.commercetools.project.sync.CliRunner.SYNC_MODULE_OPTION_DESCRIPTION;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.sphere.sdk.categories.queries.CategoryQuery;
import io.sphere.sdk.client.BadGatewayException;
import io.sphere.sdk.client.SphereClient;
import io.sphere.sdk.client.SphereClientConfig;
import io.sphere.sdk.inventory.queries.InventoryEntryQuery;
import io.sphere.sdk.products.queries.ProductQuery;
import io.sphere.sdk.producttypes.queries.ProductTypeQuery;
import io.sphere.sdk.queries.PagedQueryResult;
import io.sphere.sdk.types.queries.TypeQuery;
import io.sphere.sdk.utils.CompletableFutureUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.LoggingEvent;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

class SyncerFactoryTest {
  private static final TestLogger testLogger = TestLoggerFactory.getTestLogger(Syncer.class);
  private static ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  private static PrintStream originalSystemOut;

  @BeforeAll
  static void setupSuite() throws UnsupportedEncodingException {
    final PrintStream printStream = new PrintStream(outputStream, false, "UTF-8");
    originalSystemOut = System.out;
    System.setOut(printStream);
  }

  @AfterAll
  static void tearDownSuite() {
    System.setOut(originalSystemOut);
  }

  @AfterEach
  void tearDownTest() {
    testLogger.clearAll();
  }

  @Test
  void sync_WithNullOptionValue_ShouldCompleteExceptionallyWithIllegalArgumentException() {
    assertThat(
            SyncerFactory.of(() -> mock(SphereClient.class), () -> mock(SphereClient.class))
                .sync(null))
        .hasFailedWithThrowableThat()
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            format(
                "Blank argument supplied to \"-s\" or \"--sync\" option! %s",
                SYNC_MODULE_OPTION_DESCRIPTION));
  }

  @Test
  void sync_WithEmptyOptionValue_ShouldCompleteExceptionallyWithIllegalArgumentException() {
    assertThat(
            SyncerFactory.of(() -> mock(SphereClient.class), () -> mock(SphereClient.class))
                .sync(""))
        .hasFailedWithThrowableThat()
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            format(
                "Blank argument supplied to \"-s\" or \"--sync\" option! %s",
                SYNC_MODULE_OPTION_DESCRIPTION));
  }

  @Test
  void sync_WithUnknownOptionValue_ShouldCompleteExceptionallyWithIllegalArgumentException() {
    final String unknownOptionValue = "anyOption";

    assertThat(
            SyncerFactory.of(() -> mock(SphereClient.class), () -> mock(SphereClient.class))
                .sync(unknownOptionValue))
        .hasFailedWithThrowableThat()
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            format(
                "Unknown argument \"%s\" supplied to \"-s\" or \"--sync\" option! %s",
                unknownOptionValue, SYNC_MODULE_OPTION_DESCRIPTION));
  }

  @Test
  void sync_WithProductsArg_ShouldBuildSyncerAndExecuteSync() throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(ProductQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.sync("products");

    // assertions
    verify(sourceClient, times(1)).execute(any(ProductQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 1);

    final Condition<LoggingEvent> startLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting ProductSync"),
            "start log");

    final Condition<LoggingEvent> statisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 products were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(2)
        .haveExactly(1, startLog)
        .haveExactly(1, statisticsLog);

    assertThat(outputStream.toString("UTF-8"))
        .contains(
            "Syncing Products from CTP project with key"
                + " 'foo' to project with key 'bar' is done.");
  }

  @Test
  void sync_WithCategoriesArg_ShouldBuildSyncerAndExecuteSync()
      throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(CategoryQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.sync("categories");

    // assertions
    verify(sourceClient, times(1)).execute(any(CategoryQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 1);

    final Condition<LoggingEvent> startLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting CategorySync"),
            "start log");

    final Condition<LoggingEvent> statisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 categories were processed in total (0 created, 0 updated, "
                                + "0 failed to sync and 0 categories with a missing parent)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(2)
        .haveExactly(1, startLog)
        .haveExactly(1, statisticsLog);

    assertThat(outputStream.toString("UTF-8"))
        .contains(
            "Syncing Categories from CTP project with key"
                + " 'foo' to project with key 'bar' is done.");
  }

  @Test
  void sync_WithProductTypesArg_ShouldBuildSyncerAndExecuteSync()
      throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(ProductTypeQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.sync("productTypes");

    // assertions
    verify(sourceClient, times(1)).execute(any(ProductTypeQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 1);

    final Condition<LoggingEvent> startLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting ProductTypeSync"),
            "start log");

    final Condition<LoggingEvent> statisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 product types were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(2)
        .haveExactly(1, startLog)
        .haveExactly(1, statisticsLog);

    assertThat(outputStream.toString("UTF-8"))
        .contains(
            "Syncing ProductTypes from CTP project with key"
                + " 'foo' to project with key 'bar' is done.");
  }

  @Test
  void sync_WithTypesArg_ShouldBuildSyncerAndExecuteSync() throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(TypeQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.sync("types");

    // assertions
    verify(sourceClient, times(1)).execute(any(TypeQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 1);

    final Condition<LoggingEvent> startLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting TypeSync"),
            "start log");

    final Condition<LoggingEvent> statisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 types were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(2)
        .haveExactly(1, startLog)
        .haveExactly(1, statisticsLog);

    assertThat(outputStream.toString("UTF-8"))
        .contains(
            "Syncing Types from CTP project with key"
                + " 'foo' to project with key 'bar' is done.");
  }

  @Test
  void sync_WithInventoryEntriesArg_ShouldBuildSyncerAndExecuteSync()
      throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(InventoryEntryQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.sync("inventoryEntries");

    // assertions
    verify(sourceClient, times(1)).execute(any(InventoryEntryQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 1);

    final Condition<LoggingEvent> startLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting InventorySync"),
            "start log");

    final Condition<LoggingEvent> statisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 inventory entries were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(2)
        .haveExactly(1, startLog)
        .haveExactly(1, statisticsLog);

    assertThat(outputStream.toString("UTF-8"))
        .contains(
            "Syncing Inventories from CTP project with key"
                + " 'foo' to project with key 'bar' is done.");
  }

  @Test
  void sync_WithErrorOnFetch_ShouldCloseClientAndCompleteExceptionally() {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    final BadGatewayException badGatewayException = new BadGatewayException();
    when(sourceClient.execute(any(InventoryEntryQuery.class)))
        .thenReturn(CompletableFutureUtils.exceptionallyCompletedFuture(badGatewayException));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    final CompletionStage<Void> result = syncerFactory.sync("inventoryEntries");

    // assertions
    verify(sourceClient, times(1)).execute(any(InventoryEntryQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 0);
    assertThat(result).hasFailedWithThrowableThat().isExactlyInstanceOf(BadGatewayException.class);
  }

  @Test
  void syncAll_ShouldBuildSyncerAndExecuteSync() throws UnsupportedEncodingException {
    // preparation
    final SphereClient sourceClient = mock(SphereClient.class);
    when(sourceClient.getConfig()).thenReturn(SphereClientConfig.of("foo", "foo", "foo"));

    final SphereClient targetClient = mock(SphereClient.class);
    when(targetClient.getConfig()).thenReturn(SphereClientConfig.of("bar", "bar", "bar"));

    when(sourceClient.execute(any(ProductTypeQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));
    when(sourceClient.execute(any(TypeQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));
    when(sourceClient.execute(any(CategoryQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));
    when(sourceClient.execute(any(InventoryEntryQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));
    when(sourceClient.execute(any(ProductQuery.class)))
        .thenReturn(CompletableFuture.completedFuture(PagedQueryResult.empty()));

    final SyncerFactory syncerFactory = SyncerFactory.of(() -> sourceClient, () -> targetClient);

    // test
    syncerFactory.syncAll();

    // assertions
    verify(sourceClient, times(1)).execute(any(ProductTypeQuery.class));
    verify(sourceClient, times(1)).execute(any(TypeQuery.class));
    verify(sourceClient, times(1)).execute(any(CategoryQuery.class));
    verify(sourceClient, times(1)).execute(any(ProductQuery.class));
    verify(sourceClient, times(1)).execute(any(InventoryEntryQuery.class));
    verifyInteractionsWithClientAfterSync(sourceClient, 5);
    final String outputStringWithoutLineBreaks = outputStream.toString("UTF-8").replace("\n", "");

    final Condition<LoggingEvent> typesStartLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting TypeSync"),
            "types start log");

    final Condition<LoggingEvent> typesStatisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 types were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    final Condition<LoggingEvent> productTypesStartLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting ProductTypeSync"),
            "ProductTypes start log");

    final Condition<LoggingEvent> productTypesStatisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 product types were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    final Condition<LoggingEvent> categoriesStartLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting CategorySync"),
            "categories start log");

    final Condition<LoggingEvent> categoriesStatisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 categories were processed in total (0 created, 0 updated, "
                                + "0 failed to sync and 0 categories with a missing parent)."),
            "statistics log");

    final Condition<LoggingEvent> productsStartLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting ProductSync"),
            "products start log");

    final Condition<LoggingEvent> productsStatisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 products were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    final Condition<LoggingEvent> inventoriesStartLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent.getMessage().contains("Starting InventorySync"),
            "inventories start log");

    final Condition<LoggingEvent> inventoriesStatisticsLog =
        new Condition<>(
            loggingEvent ->
                Level.INFO.equals(loggingEvent.getLevel())
                    && loggingEvent
                        .getMessage()
                        .contains(
                            "Summary: 0 inventory entries were processed in total (0 created, 0 updated "
                                + "and 0 failed to sync)."),
            "statistics log");

    assertThat(testLogger.getAllLoggingEvents())
        .hasSize(10)
        .haveExactly(1, typesStartLog)
        .haveExactly(1, productTypesStartLog)
        .haveExactly(1, categoriesStartLog)
        .haveExactly(1, productsStartLog)
        .haveExactly(1, inventoriesStartLog)
        .haveExactly(1, typesStatisticsLog)
        .haveExactly(1, productTypesStatisticsLog)
        .haveExactly(1, categoriesStatisticsLog)
        .haveExactly(1, productsStatisticsLog)
        .haveExactly(1, inventoriesStatisticsLog);

    assertThat(outputStringWithoutLineBreaks)
        .containsSequence(
            "Syncing ProductTypes from CTP project with key 'foo' to project with key 'bar' is done.",
            "Syncing Types from CTP project with key 'foo' to project with key 'bar' is done.",
            "Syncing Categories from CTP project with key 'foo' to project with key 'bar' is done.",
            "Syncing Products from CTP project with key 'foo' to project with key 'bar' is done.",
            "Syncing Inventories from CTP project with key 'foo' to project with key 'bar' is done.");
  }

  private void verifyInteractionsWithClientAfterSync(
      @Nonnull final SphereClient client, final int expectedNumberOfGetConfigCalls) {

    // Verify config is accessed for the success message after sync:
    // " example: Syncing products from CTP project with key 'x' to project with key 'y' is done","
    verify(client, times(expectedNumberOfGetConfigCalls)).getConfig();
    verify(client, times(1)).close();
    verifyNoMoreInteractions(client);
  }
}

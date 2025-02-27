/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.viewconfig;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.internal.view.AttributesProcessor;
import io.opentelemetry.sdk.metrics.internal.view.ViewRegistryBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class ViewConfigTest {

  @Test
  void registerViews_FullConfig() {
    SdkMeterProviderBuilder builder = SdkMeterProvider.builder();

    ViewConfig.registerViews(builder, resourceFileInputStream("full-config.yaml"));

    assertThat(builder)
        .extracting(
            "viewRegistryBuilder", as(InstanceOfAssertFactories.type(ViewRegistryBuilder.class)))
        .extracting("orderedViews", as(InstanceOfAssertFactories.list(Object.class)))
        .hasSize(2);
  }

  @Test
  void loadViewConfig_FullConfig() {
    List<ViewConfigSpecification> viewConfigSpecs =
        ViewConfig.loadViewConfig(resourceFileInputStream("full-config.yaml"));

    assertThat(viewConfigSpecs)
        .satisfiesExactly(
            viewConfigSpec -> {
              SelectorSpecification selectorSpec = viewConfigSpec.getSelectorSpecification();
              assertThat(selectorSpec.getInstrumentName()).isEqualTo("name1");
              assertThat(selectorSpec.getInstrumentType()).isEqualTo(InstrumentType.COUNTER);
              assertThat(selectorSpec.getMeterName()).isEqualTo("meterName1");
              assertThat(selectorSpec.getMeterVersion()).isEqualTo("1.0.0");
              assertThat(selectorSpec.getMeterSchemaUrl()).isEqualTo("http://example1.com");
              ViewSpecification viewSpec = viewConfigSpec.getViewSpecification();
              assertThat(viewSpec.getName()).isEqualTo("name1");
              assertThat(viewSpec.getDescription()).isEqualTo("description1");
              assertThat(viewSpec.getAggregation()).isEqualTo("sum");
              assertThat(viewSpec.getAttributeKeys()).containsExactly("foo", "bar");
            },
            viewConfigSpec -> {
              SelectorSpecification selectorSpec = viewConfigSpec.getSelectorSpecification();
              assertThat(selectorSpec.getInstrumentName()).isEqualTo("name2");
              assertThat(selectorSpec.getInstrumentType()).isEqualTo(InstrumentType.COUNTER);
              assertThat(selectorSpec.getMeterName()).isEqualTo("meterName2");
              assertThat(selectorSpec.getMeterVersion()).isEqualTo("2.0.0");
              assertThat(selectorSpec.getMeterSchemaUrl()).isEqualTo("http://example2.com");
              ViewSpecification viewSpec = viewConfigSpec.getViewSpecification();
              assertThat(viewSpec.getName()).isEqualTo("name2");
              assertThat(viewSpec.getDescription()).isEqualTo("description2");
              assertThat(viewSpec.getAggregation()).isEqualTo("last_value");
              assertThat(viewSpec.getAttributeKeys()).containsExactly("baz", "qux");
            });
  }

  @Test
  void loadViewConfig_Invalid() {
    assertThatThrownBy(
            () -> ViewConfig.loadViewConfig(resourceFileInputStream("empty-view-config.yaml")))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("Failed to parse view config")
        .hasRootCauseMessage("view is required");
    assertThatThrownBy(
            () -> ViewConfig.loadViewConfig(resourceFileInputStream("empty-selector-config.yaml")))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("Failed to parse view config")
        .hasRootCauseMessage("selector is required");
  }

  @Test
  void toView_Empty() {
    View view = ViewConfig.toView(ViewSpecification.builder().build());
    assertThat(view).isEqualTo(View.builder().build());
  }

  @Test
  void toView() {
    View view =
        ViewConfig.toView(
            ViewSpecification.builder()
                .name("name")
                .description("description")
                .aggregation("sum")
                .attributeKeys(Arrays.asList("foo", "bar"))
                .build());
    assertThat(view.getName()).isEqualTo("name");
    assertThat(view.getDescription()).isEqualTo("description");
    assertThat(view.getAggregation()).isEqualTo(Aggregation.sum());
    assertThat(view)
        .extracting(
            "attributesProcessor", as(InstanceOfAssertFactories.type(AttributesProcessor.class)))
        .satisfies(
            attributesProcessor -> {
              assertThat(
                      attributesProcessor.process(
                          Attributes.builder()
                              .put("foo", "val")
                              .put("bar", "val")
                              .put("baz", "val")
                              .build(),
                          Context.current()))
                  .containsEntry("foo", "val")
                  .containsEntry("bar", "val")
                  .satisfies(
                      (Consumer<Attributes>)
                          attributes ->
                              assertThat(attributes.get(AttributeKey.stringKey("baz"))).isBlank());
            });
  }

  @Test
  void toAggregation() {
    assertThatThrownBy(() -> ViewConfig.toAggregation("foo"))
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("Error creating aggregation");
  }

  @Test
  void toInstrumentSelector() {
    InstrumentSelector selector =
        ViewConfig.toInstrumentSelector(
            SelectorSpecification.builder()
                .instrumentName("name")
                .instrumentType(InstrumentType.COUNTER)
                .meterName("meterName")
                .meterVersion("meterVersion")
                .meterSchemaUrl("http://example.com")
                .build());

    assertThat(selector.getInstrumentName()).isEqualTo("name");
    assertThat(selector.getInstrumentType()).isEqualTo(InstrumentType.COUNTER);
    assertThat(selector.getMeterName()).isEqualTo("meterName");
    assertThat(selector.getMeterVersion()).isEqualTo("meterVersion");
    assertThat(selector.getMeterSchemaUrl()).isEqualTo("http://example.com");
  }

  private static InputStream resourceFileInputStream(String resourceFileName) {
    URL resourceUrl = ViewConfigTest.class.getResource("/" + resourceFileName);
    if (resourceUrl == null) {
      throw new IllegalStateException("Could not find resource file: " + resourceFileName);
    }
    String path = resourceUrl.getFile();
    try {
      return new FileInputStream(path);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("File not found: " + path, e);
    }
  }
}

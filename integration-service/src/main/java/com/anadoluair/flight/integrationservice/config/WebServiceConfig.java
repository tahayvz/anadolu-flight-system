package com.anadoluair.flight.integrationservice.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Spring Web Services Configuration for SOAP endpoints.
 * This enables the /ws/* path for SOAP calls and auto-generates WSDL.
 */
@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    /**
     * Register the MessageDispatcherServlet for handling SOAP requests.
     * All SOAP endpoints will be accessible under /ws/*
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Define the WSDL for the Flight service.
     * Accessible at: http://localhost:8081/ws/flights.wsdl
     */
    @Bean(name = "flights")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema flightSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("FlightPort");
        wsdl11Definition.setLocationUri("/ws");
        wsdl11Definition.setTargetNamespace("http://anadoluair.example/flight/soap");
        wsdl11Definition.setSchema(flightSchema);
        return wsdl11Definition;
    }

    /**
     * Load the XSD schema for JAXB to generate Java classes.
     */
    @Bean
    public XsdSchema flightSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/flight.xsd"));
    }
}

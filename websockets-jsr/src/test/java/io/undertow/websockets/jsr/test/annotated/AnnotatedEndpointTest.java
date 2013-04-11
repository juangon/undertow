/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.websockets.jsr.test.annotated;

import java.net.URI;

import javax.websocket.Session;

import io.undertow.client.HttpClient;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.test.utils.DefaultServer;
import io.undertow.util.ConcreteIoFuture;
import io.undertow.websockets.jsr.bootstrap.WebSocketDeployer;
import io.undertow.websockets.jsr.bootstrap.WebSocketDeployment;
import io.undertow.websockets.jsr.bootstrap.WebSocketDeploymentInfo;
import io.undertow.websockets.utils.FrameChecker;
import io.undertow.websockets.utils.WebSocketTestClient;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xnio.ByteBufferSlicePool;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
@RunWith(DefaultServer.class)
public class AnnotatedEndpointTest {

    private static WebSocketDeployment deployment;

    @BeforeClass
    public static void setup() throws Exception {

        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(AnnotatedEndpointTest.class.getClassLoader())
                .setContextPath("/")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("servletContext.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER);


        WebSocketDeploymentInfo info = new WebSocketDeploymentInfo();
        info.setBufferPool(new ByteBufferSlicePool(100, 1000));
        deployment = WebSocketDeployment.create(info, HttpClient.create(DefaultServer.getWorker(), OptionMap.EMPTY));
        deployment.getDeploymentInfo().addAnnotatedEndpoints(AnnotatedTestEndpoint.class, AnnotatedClientEndpoint.class);
        WebSocketDeployer.deploy(deployment, builder, AnnotatedEndpointTest.class.getClassLoader());


        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();


        DefaultServer.setRootHandler(manager.start());
    }

    @AfterClass
    public static void after() {
        deployment = null;
    }

    @org.junit.Test
    public void testStringOnMessage() throws Exception {
        final byte[] payload = "hello".getBytes();
        final ConcreteIoFuture latch = new ConcreteIoFuture();

        WebSocketTestClient client = new WebSocketTestClient(WebSocketVersion.V13, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/chat/Stuart"));
        client.connect();
        client.send(new TextWebSocketFrame(ChannelBuffers.wrappedBuffer(payload)), new FrameChecker(TextWebSocketFrame.class, "hello Stuart".getBytes(), latch));
        latch.get();
        client.destroy();
    }

    @org.junit.Test
    public void testAnnotatedClientEndpoint() throws Exception {


        Session session = deployment.getContainer().connectToServer(AnnotatedClientEndpoint.class, new URI("ws://" + DefaultServer.getHostAddress("default") + ":" + DefaultServer.getHostPort("default") + "/chat/Bob"));

        Assert.assertEquals("hi Bob", AnnotatedClientEndpoint.message());

        session.close();
        Assert.assertEquals("CLOSED", AnnotatedClientEndpoint.message());
    }
}

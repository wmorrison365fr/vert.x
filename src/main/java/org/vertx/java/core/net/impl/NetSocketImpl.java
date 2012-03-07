/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.core.net.impl;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.CharsetUtil;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.net.NetSocket;

import java.io.File;
import java.nio.charset.Charset;
import java.util.UUID;

public class NetSocketImpl extends NetSocket {

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Void> drainHandler;
  private Handler<Message<Buffer>> writeHandler;

  public NetSocketImpl(Channel channel, Context context, Thread th) {
    super(channel, UUID.randomUUID().toString(), context, th);
    if (EventBus.instance != null) {
      writeHandler = new Handler<Message<Buffer>>() {
        public void handle(Message<Buffer> msg) {
          writeBuffer(msg.body);
        }
      };
      EventBus.instance.registerHandler(writeHandlerID, writeHandler);
    }
  }


  public void writeBuffer(Buffer data) {
    doWrite(data.getChannelBuffer());
  }

  public NetSocket write(Buffer data) {
    doWrite(data.getChannelBuffer());
    return this;
  }

  public NetSocket write(String str) {
    doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8));
    return this;
  }

  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc)));
    }
    return this;
  }

  public NetSocket write(Buffer data, Handler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(data.getChannelBuffer()));
    return this;
  }

  public NetSocket write(String str, Handler<Void> doneHandler) {
    addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, CharsetUtil.UTF_8)));
    return this;
  }

  public NetSocket write(String str, String enc, Handler<Void> doneHandler) {
    if (enc == null) {
      write(str, enc);
    } else {
      addFuture(doneHandler, doWrite(ChannelBuffers.copiedBuffer(str, Charset.forName(enc))));
    }
    return this;
  }

  public void dataHandler(Handler<Buffer> dataHandler) {
    checkThread();
    this.dataHandler = dataHandler;
  }

  public void endHandler(Handler<Void> endHandler) {
    checkThread();
    this.endHandler = endHandler;
  }

  public void drainHandler(Handler<Void> drainHandler) {
    checkThread();
    this.drainHandler = drainHandler;
    Vertx.instance.nextTick(new SimpleHandler() {
      public void handle() {
        callDrainHandler(); //If the channel is already drained, we want to call it immediately
      }
    });
  }

  public void sendFile(String filename) {
    checkThread();
    File f = new File(filename);
    super.sendFile(f);
  }

  protected Context getContext() {
    return super.getContext();
  }

  protected void handleClosed() {
    setContext();

    if (endHandler != null) {
      try {
        endHandler.handle(null);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
    super.handleClosed();
    if (EventBus.instance != null) {
      EventBus.instance.unregisterHandler(writeHandlerID, writeHandler);
    }
  }

  void handleInterestedOpsChanged() {
    setContext();
    callDrainHandler();
  }

  void handleDataReceived(Buffer data) {
    if (dataHandler != null) {
      setContext();
      try {
        dataHandler.handle(data);
      } catch (Throwable t) {
        handleHandlerException(t);
      }
    }
  }

  //Close without checking thread - used when server is closed
  void internalClose() {
    channel.close();
  }

  private ChannelFuture doWrite(ChannelBuffer buff) {
    checkThread();
    return channel.write(buff);
  }

  private void callDrainHandler() {
    if (drainHandler != null) {
      if (channel.isWritable()) {
        try {
          drainHandler.handle(null);
        } catch (Throwable t) {
          handleHandlerException(t);
        }
      }
    }
  }
}


package com.mauriciotogneri.common;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.MessageListener;
import com.google.android.gms.wearable.MessageApi.SendMessageResult;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.mauriciotogneri.common.model.Message;

import java.util.concurrent.TimeUnit;

public class WearableConnectivity
{
    private boolean isConnected = false;
    private final GoogleApiClient apiClient;

    private static final int TIMEOUT = 1000 * 10;

    public WearableConnectivity(Context context, final OnConnectionEvent onConnectionEvent, final OnMessageReceived onMessageReceived)
    {
        this.apiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(new ConnectionCallbacks()
        {
            @Override
            public void onConnected(Bundle connectionHint)
            {
                onClientConnected(onConnectionEvent, onMessageReceived);
            }

            @Override
            public void onConnectionSuspended(int cause)
            {
                onConnectionEvent.onConnectedFail();
            }
        }).addOnConnectionFailedListener(new OnConnectionFailedListener()
        {
            @Override
            public void onConnectionFailed(ConnectionResult result)
            {
                onConnectionEvent.onConnectedFail();
            }
        }).addApi(Wearable.API).build();
    }

    public synchronized void connect()
    {
        apiClient.connect();
    }

    public synchronized void disconnect()
    {
        apiClient.disconnect();
    }

    private synchronized void onClientConnected(final OnConnectionEvent onConnectionEvent, final OnMessageReceived onMessageReceived)
    {
        if (!isConnected)
        {
            isConnected = true;

            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        PendingResult<Status> pendingResult = Wearable.MessageApi.addListener(apiClient, new MessageListener()
                        {
                            @Override
                            public void onMessageReceived(MessageEvent messageEvent)
                            {
                                String nodeId = messageEvent.getSourceNodeId();
                                String path = messageEvent.getPath();
                                byte[] payload = messageEvent.getData();

                                final Message message = new Message(nodeId, path, payload);

                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        onMessageReceived.onMessageReceived(message);
                                    }
                                });
                            }
                        });
                        pendingResult.setResultCallback(new ResultCallback<Status>()
                        {
                            @Override
                            public void onResult(Status status)
                            {
                                if (status.isSuccess())
                                {
                                    onConnectionEvent.onConnectedSuccess();
                                }
                                else
                                {
                                    onConnectionEvent.onConnectedFail();
                                }
                            }
                        });
                        //pendingResult.await();
                    }
                    catch (Exception e)
                    {
                        onConnectionEvent.onConnectedFail();
                    }
                }
            }).start();
        }
    }

    public synchronized void sendMessage(final Message message, final ResultCallback<SendMessageResult> callback)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                apiClient.blockingConnect(TIMEOUT, TimeUnit.MILLISECONDS);

                try
                {
                    PendingResult<MessageApi.SendMessageResult> pendingResult = Wearable.MessageApi.sendMessage(apiClient, message.getNodeId(), message.getPath(), message.getPayloadAsBytes());

                    if (callback != null)
                    {
                        pendingResult.setResultCallback(callback);
                    }

                    pendingResult.await();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public synchronized void sendMessage(Message message)
    {
        sendMessage(message, null);
    }

    public synchronized void getDefaultDeviceNode(final OnDeviceNodeDetected onDeviceNodeDetected)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                apiClient.blockingConnect(TIMEOUT, TimeUnit.MILLISECONDS);

                try
                {
                    NodeApi.GetConnectedNodesResult result = Wearable.NodeApi.getConnectedNodes(apiClient).await();

                    for (Node node : result.getNodes())
                    {
                        if (node.isNearby())
                        {
                            onDeviceNodeDetected.onDefaultDeviceNode(node.getId());
                            break;
                        }
                    }
                }
                catch (Exception e)
                {
                    onDeviceNodeDetected.onDefaultDeviceNode(null);
                }
            }
        }).start();
    }

    public interface OnMessageReceived
    {
        void onMessageReceived(Message message);
    }

    public interface OnConnectionEvent
    {
        void onConnectedSuccess();

        void onConnectedFail();
    }

    public interface OnDeviceNodeDetected
    {
        void onDefaultDeviceNode(String nodeId);
    }
}
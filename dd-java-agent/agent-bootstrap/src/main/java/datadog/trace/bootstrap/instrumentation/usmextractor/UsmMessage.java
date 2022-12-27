package datadog.trace.bootstrap.instrumentation.usmextractor;

import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import sun.security.ssl.SSLSocketImpl;

import java.net.Inet4Address;
import java.net.Inet6Address;

public interface UsmMessage {

  public enum MessageType{
    //message created from hooks on from read / write functions of AppInputStream and AppOutputStream respectively
    REQUEST,

    //message created from a hook on close method of the SSLSocketImpl
    CLOSE_CONNECTION,
  }

  public enum ProtocolType{
    //message created from hooks on from read / write functions of AppInputStream and AppOutputStream respectively
    IPv4,

    //message created from a hook on close method of the SSLSocketImpl
    IPv6,
  }

  //TODO: sync with systemprobe code
  static final NativeLong USM_IOCTL_ID = new NativeLong(0xda7ad09L);;

  Pointer getBufferPtr();

  int getMessageSize();
  int dataSize();
  boolean validate();

  abstract class BaseUsmMessage implements UsmMessage{

    //total message size [4 bytes] || Message type [4 bytes]
    static final int PROTOCOL_METADATA_SIZE = 8;

    // size of the connection struct:
    // Connection Info length [4 bytes] || Protocol Type [4 bytes] || Src IP [4 bytes] || Src Port [4 bytes] || Dst IP [4 bytes] || Dst port [4 bytes]
    static final int CONNECTION_INFO_SIZE = 24;

    //pointer to native memory buffer
    protected Pointer pointer;
    protected int totalMessageSize;
    protected int offset;
    private MessageType messageType;


    @Override
    public final Pointer getBufferPtr(){
      return pointer;
    }

    @Override
    public boolean validate() {
      if (offset != getMessageSize()){
        System.out.println("invalid message size, expected: " + getMessageSize() + " actual: " + offset);
        return false;
      }
      return true;
    }

    public final int getMessageSize(){
      return totalMessageSize;
    }

    public BaseUsmMessage(MessageType type, SSLSocketImpl socket){
      messageType = type;

      totalMessageSize = PROTOCOL_METADATA_SIZE + CONNECTION_INFO_SIZE + dataSize();
      System.out.println("Allocating " + totalMessageSize + " memory");
      pointer = new Memory(totalMessageSize);
      offset = 0;

      //write totalMessageSize
      pointer.setInt(offset,totalMessageSize);
      offset+=4;

      //encode message type
      pointer.setInt(offset,messageType.ordinal());
      offset+=4;

      encodeConnection(socket);
    }

    private void encodeConnection(SSLSocketImpl socket){
      ProtocolType protocolType = ProtocolType.IPv4;
      if (socket.getLocalAddress() instanceof Inet6Address){
        protocolType = ProtocolType.IPv6;
      }

      //write connection length
      pointer.setInt(offset,CONNECTION_INFO_SIZE-4);
      offset+=4;

      //write protocol type
      pointer.setInt(offset,protocolType.ordinal());
      offset+=4;

      //write local ip + port
      pointer.write(offset,socket.getLocalAddress().getAddress(),0,4);
      offset += 4;
      pointer.setInt(offset, socket.getLocalPort());
      offset += 4;

      //write remote ip + port
      pointer.write(offset,socket.getInetAddress().getAddress(),0,4);
      offset += 4;
      pointer.setInt(offset, socket.getPeerPort());
      offset += 4;
    }
  }

  public class CloseConnectionUsmMessage extends BaseUsmMessage{

    public CloseConnectionUsmMessage(SSLSocketImpl socket) {
      super(MessageType.CLOSE_CONNECTION, socket);
    }

    @Override
    public int dataSize() {
      //no actual data for close connection message, only the connection tuple
      return 0;
    }
  }

  public class RequestUsmMessage extends BaseUsmMessage{

    //120 bytes is the max fragment size we allow in SystemProbe
    static final int MAX_HTTPS_BUFFER_SIZE = 120;
    public RequestUsmMessage(SSLSocketImpl socket, byte[] buffer, int bufferOffset, int len) {
      super(MessageType.REQUEST, socket);
      //write payload actual length
      if (len - bufferOffset <= MAX_HTTPS_BUFFER_SIZE){
        pointer.setInt(offset,len);
        offset += 4;

        pointer.write(offset,buffer,bufferOffset,len);
        offset+=len;

      }
      else{
        pointer.setInt(offset,MAX_HTTPS_BUFFER_SIZE);
        offset += 4;
        pointer.write(offset,buffer,bufferOffset,MAX_HTTPS_BUFFER_SIZE);
        offset += MAX_HTTPS_BUFFER_SIZE;
      }
    }

    @Override
    public int dataSize() {
      //max buffer preceded by the actual length [4 bytes] of the buffer
      return MAX_HTTPS_BUFFER_SIZE+4;
    }
  }

}

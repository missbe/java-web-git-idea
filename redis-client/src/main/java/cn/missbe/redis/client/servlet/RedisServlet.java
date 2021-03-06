package cn.missbe.redis.client.servlet;

import cn.missbe.redis.client.App;
import cn.missbe.redis.client.bean.HashBean;
import cn.missbe.redis.client.dto.JsonBaseResult;
import cn.missbe.redis.client.util.CommandProcessUtil;
import cn.missbe.util.IOUtils;
import cn.missbe.util.PrintUtil;
import cn.missbe.util.SystemLog;
import com.alibaba.fastjson.JSON;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 *   Description:java_code
 *   mail: love1208tt@foxmail.com
 *   Copyright (c) 2018. missbe
 *   This program is protected by copyright laws.
 *   Program Name:redisjava
 *   @Date:18-8-29 上午10:50
 *   @author lyg
 *   @version 1.0
 *   @Description Servlet负责对前台请求校验，发送命令到主服务器
 **/
@WebServlet(urlPatterns = {"/redis/data/cached"})
public class RedisServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
       String msg;
       String command = req.getParameter("command");
       String key = req.getParameter("key");
       String value = req.getParameter("value");

       resp.setContentType("application/json");
       resp.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter ps = resp.getWriter();
        if(command == null || key == null){
            msg = "提示:命令不能为空,请输入命令.";
            ///向前端发送JSON数据
            ps.write(JSON.toJSONString(new JsonBaseResult(msg,true)));
            ps.flush();
            ps.close();
            return;
        }

       ////对传送到服务器的键数据进行UTF-8编码，以免乱码
       key  = new String(key.getBytes(StandardCharsets.UTF_8));

       ////客户端检查命令格式
       boolean isCommand =  CommandProcessUtil.isCommand(command);
       if(!isCommand){
           msg = "提示:你输入的命令不合法.";
           ///向前端发送JSON数据
           ps.write(JSON.toJSONString(new JsonBaseResult(msg,true)));
           ps.flush();
           ps.close();
           return;
       }

       CRC32 crc32 = new CRC32();
       crc32.update(key.getBytes());
       ////向服务器发送命令和取得数据
       HashBean bean  = getServerHashBean((int) (crc32.getValue() % App.HASH_LENGTH));
       if(command.equalsIgnoreCase("get") || command.equalsIgnoreCase("del") || command.equalsIgnoreCase("expire")) {
           command = command + " " + key;
       }else {
           ////对传送到服务器的值数据进行UTF-8编码，以免乱码
           value  = new String(value.getBytes(StandardCharsets.UTF_8));
           command = command + " " + key + " " + value;
       }
        ///向对应服务器请求数据
       msg = processServerCommand(bean, command);///向服务器发请求得到回应
        ///向前端发送JSON数据
        ps.write(JSON.toJSONString(new JsonBaseResult(msg,true)));
        ps.flush();
        ps.close();

    }
    String processServerCommand(HashBean bean, String command) {
        if(bean == null){
            return "服务器信息加载失败，哈希槽分布检查.";
        }
        ////与服务器通信部分
        Socket socket;
        try {
            socket = new Socket(bean.getIp(), bean.getPort());
            bean.setFailCount(0); ///连接失败次数置为0
        } catch (IOException e) {
            String msg = "服务器连接失败-可能服务器已经关闭或者请求忙-请过段时间尝试连接";
            PrintUtil.print(msg, SystemLog.Level.error);
            bean.setFailCount(bean.getCount()+1);
            return msg;
        }
        String msg = null ;

        try (        PrintStream ps = new PrintStream(socket.getOutputStream())
        ){
            ps.println(command);///发送命令到服务器
            msg = IOUtils.parseStream(socket.getInputStream(), App.SERVER_OK);
            socket.close();
        } catch (IOException e) {
            PrintUtil.print("服务器端已经关闭或出现错误.结束访问", SystemLog.Level.error);
        }
        return msg == null ? "服务器消息为空." : msg.equals("1002") ? "命令格式不正确,请检查命令" : msg;
    }
    /**
     * 根据哈希值得到对应服务器IP地址和端口
     * Note:每调用一次该方法，该Hash值对应的对象数量就会加一
     * @param hash hash值
     * @return 服务器信息
     */
    HashBean getServerHashBean(int hash){
        for(HashBean bean : App.getHashBeans()){
            if(bean.isHash(hash)){
                bean.setCount(bean.getCount()+1);
                return bean;
            }
        }
        return null;
    }
}

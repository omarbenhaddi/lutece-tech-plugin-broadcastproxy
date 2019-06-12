/*
 * Copyright (c) 2002-2019, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.broadcastproxy.business.providers.hubscore;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.paris.lutece.plugins.broadcastproxy.business.Feed;
import java.util.HashMap;
import java.util.Map;

import fr.paris.lutece.plugins.broadcastproxy.business.IBroadcastProvider;
import fr.paris.lutece.plugins.broadcastproxy.business.Subscription;
import fr.paris.lutece.plugins.broadcastproxy.service.Constants;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.lang.StringUtils;

public class HubScoreProvider implements IBroadcastProvider
{

    // Constants
    private static final String PROVIDER_NAME = "HubScore";
    private static final String CONSTANT_KEY_RECORDS = "records";

    // instance variables
    private HubScoreAPI _hubScoreAPI;

    /**
     * Constructor
     */
    public HubScoreProvider( )
    {
        _hubScoreAPI = new HubScoreAPI( );
    }

    @Override
    public String getName( )
    {
        return PROVIDER_NAME;
    }

    @Override
    public boolean subscribe( String userId, String subscriptionId, String typeSubsciption ) throws Exception
    {

        try
        {
            _hubScoreAPI.manageUser( userId, Constants.TYPE_NEWSLETTER, Constants.ACTION_ADD, false );
        }
        catch( Exception e )
        {
            // try with a new token
            _hubScoreAPI.manageUser( userId, Constants.TYPE_NEWSLETTER, Constants.ACTION_ADD, true );
        }
        return true;
    }

    @Override
    public boolean unsubscribe( String userName, String subscriptionId, String typeSubscription ) throws Exception
    {
        Subscription sub = new Subscription( );
        sub.setUserName( userName );
        sub.setName( subscriptionId );
        sub.setType( typeSubscription );

        return update( sub );
    }

    @Override
    public boolean update( Subscription sub ) throws Exception
    {
        Map<String, String> mapDatas = new HashMap<>( );

        String name = sub.getName( );
        
        if ( !name.startsWith("Optin_") ) name = "Optin_" + name;
        String active = ( sub.isActive( ) ? "1" : "0" );
        mapDatas.put( name, active );

        // add date of update
        if ( sub.isActive( ) )
        {
            mapDatas.put( "Date_Consentement_" + name.substring( 6 ), getFormattedCurrentLocaleDateTime( ) );
        }
        else
        {
            mapDatas.put( "Date_Desinscription_" + name.substring( 6 ), getFormattedCurrentLocaleDateTime( ) );
        }

        // add themes
        if ( sub.getData( ) != null && sub.getData( ).size( ) > 0 )
        {
            String tab = sub.getData( ).toString( ).replaceAll( " ", "" );
            String tabName = name.substring( 6 ); // name without "Optin_"

            // add themes
            mapDatas.put( tabName, tab );
        }

        try
        {
            _hubScoreAPI.updateSubscribtions( sub.getUserName( ), mapDatas, sub.getType( ), false );
        }
        catch( Exception e )
        {
            // retry with refreshed token
            _hubScoreAPI.updateSubscribtions(sub.getUserName( ), mapDatas, sub.getType( ), true );
        }

        return true;
    }

    @Override
    public List<Subscription> getUserSubscriptionsAsList( String userId, String typeSubsciption ) throws Exception
    {
        String userSubscriptionsList = null;

        try
        {
            userSubscriptionsList = _hubScoreAPI.getUserSubscriptions( userId, typeSubsciption, false );
        }
        catch( Exception e )
        {
            // try with new token
            userSubscriptionsList = _hubScoreAPI.getUserSubscriptions( userId, typeSubsciption, true );
        }

        return buildSubscriptionList( userSubscriptionsList );
    }

    @Override
    public String getUserSubscriptionsAsJson( String userId, String typeSubsciption ) throws Exception
    {
        String userSubscriptionsList = null;

        try
        {
            userSubscriptionsList = _hubScoreAPI.getUserSubscriptions( userId, typeSubsciption, false );
        }
        catch( Exception e )
        {
            // try with new token
            userSubscriptionsList = _hubScoreAPI.getUserSubscriptions( userId, typeSubsciption, true );
        }

        return buildJson( buildSubscriptionList( userSubscriptionsList ) );
    }

    @Override
    public boolean updateSubscribtions( List<Subscription> subscriptionsList ) throws Exception
    {
        for ( Subscription sub : subscriptionsList )
        {
            boolean success = update( sub );

            if ( !success )
                return false;
        }

        return true;
    }

    /**
     * Build JSON response from subscription beans list
     * 
     * @param the
     *            list
     * @return a JSON String
     */
    private String buildJson( List<Subscription> subList ) throws IOException
    {
        ObjectMapper mapper = new ObjectMapper( );
        ObjectNode jsonResult = mapper.createObjectNode( );

        String jsonList = mapper.writeValueAsString( subList );

        jsonResult.putPOJO( "subscriptions", jsonList );

        return jsonResult.toString( );
    }

    /**
     * Parse JSON response as subscription beans
     * 
     * @param jsonResponse
     * @return a JSON String
     */
    private List<Subscription> buildSubscriptionList( String jsonResponse ) throws IOException
    {

        ObjectMapper mapper = new ObjectMapper( );
        List<Subscription> subscriptionList = new ArrayList<>( );

        JsonNode jsonNode = mapper.readTree( jsonResponse );
        ArrayNode arrayRecordsNode = (ArrayNode) jsonNode.get( CONSTANT_KEY_RECORDS );

        if ( arrayRecordsNode != null && arrayRecordsNode.size( ) > 0 )
        {
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = arrayRecordsNode.get( 0 ).fields( );
            while ( fieldsIterator.hasNext( ) )
            {
                Map.Entry<String, JsonNode> field = fieldsIterator.next( );

                String key = field.getKey( );

                // subscription name key must start with "Optin_"
                if ( key.startsWith( "Optin_" ) )
                {
                    String subName = key.substring( 6 );
                    String subState = field.getValue( ).asText( );

                    Subscription sub = new Subscription( );
                    sub.setName( subName );
                    sub.setActive( "1".equals( subState ) );

                    if ( arrayRecordsNode.get( 0 ).get( subName ) != null )
                    {
                        String data = arrayRecordsNode.get( 0 ).get( subName ).asText( );
                        String [ ] themes = data.split( "," );

                        Map<String, String>  themeList = new HashMap<>();
                        for ( String theme : themes )
                        {
                            themeList.put( theme, theme );
                        }

                        sub.setData( themeList );
                    }

                    subscriptionList.add( sub );
                }
            }
        }

        return subscriptionList;
    }

    /**
     * Parse JSON response like :
     * 
     * {"count":"1","records":[{"id":"123456","Email":"XXX.YYY@ZZZ.com","Optin_Paris":"1","Date_Consentement_Paris":"2019-06-07 14:34:45","Optin_QFAP":"1",
     * "Date_Consentement_QFAP"
     * :"2019-06-07 14:34:45","Optin_Alerte":"1","Alerte":"parcs_et_jardins,Paris_sport_vacances","Date_Consentement_Alerte":"2019-06-07 14:34:45"
     * ,"hubBlacklist":false}]}
     * 
     * and build generic MAP response as pairs of (id,name)
     * 
     * @param jsonResponse
     * @return a JSON String
     */
    private Map<String, String> buildMap( String jsonResponse ) throws IOException
    {
        Map<String, String> map = new HashMap<>( );
        ObjectMapper mapper = new ObjectMapper( );
        JsonNode jsonNode = null;

        jsonNode = mapper.readTree( jsonResponse );

        ArrayNode arrayNode = (ArrayNode) jsonNode.get( CONSTANT_KEY_RECORDS );

        if ( arrayNode != null && arrayNode.size( ) > 0 )
        {
            Iterator<Map.Entry<String, JsonNode>> fieldsIterator = arrayNode.get( 0 ).fields( );
            while ( fieldsIterator.hasNext( ) )
            {
                Map.Entry<String, JsonNode> field = fieldsIterator.next( );

                String key = field.getKey( );

                // subscription name key must start with "Optin_"
                if ( key.startsWith( "Optin_" ) )
                {
                    String subName = key;
                    String subState = field.getValue( ).asText( );
                    map.put( subName, subState );
                }

                // Patricular case : "Alerte" subscription has datas
                if ( key.equals( "Alerte" ) )
                {
                    String subName = key;
                    String subState = field.getValue( ).asText( );
                    map.put( subName, subState );
                }

            }
        }

        return map;
    }

    /**
     * get formatted current locale date time
     * 
     * @return the formatted date
     */
    private String getFormattedCurrentLocaleDateTime( )
    {
        LocalDateTime now = LocalDateTime.now( );
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

        return now.format( formatter );
    }

    @Override
    public List<Feed> getFeeds() {
        List<Feed> list = new ArrayList<>();
        
        /* example
broadcastproxy.hubscore.feedsType=ALERT,NEWSLETTER
broadcastproxy.hubscore.feeds.type.ALERT=Paris,QFAP,Alerte
broadcastproxy.hubscore.feeds.type.NEWSLETTER=Budget_Participatif,Carte_Citoyenne,Nuit_Debats,Lettre_Climat,Quartier_Populaire,asso.paris
broadcastproxy.hubscore.feeds.type.ALERT.Alerte.data==ateliers_beaux_arts,bmo,circulation,CMA,collecte_des_dechets,conservatoires,elections,parcs_et_jardins,Paris_sport_vacances,senior_plus,stationnement_residentiel,universite_permanente,vacances_arc_en_ciel
*/
        String[] feedsTypes = AppPropertiesService.getProperty( "broadcastproxy.hubscore.feedsType", "" ).split(",");
        
        for (String feedType : feedsTypes)
        {
            String[] feedNames = AppPropertiesService.getProperty( "broadcastproxy.hubscore.feeds.type."+ feedType, "" ).split(",");
            for (String feedName : feedNames )
            {
                Feed feed =  new Feed(feedName, feedName, feedType);
                String datas[] = AppPropertiesService.getProperty( "broadcastproxy.hubscore.feeds.type."+ feedType + "." + feedName + ".data", "" ).split(",");
                if (datas.length>0)
                {
                    Map<String,String> mapData = new HashMap<>();
                    for (String data : datas )
                    {
                        if ( !StringUtils.isBlank( data ) )  mapData.put( data, data );
                    }
                    feed.setData( mapData );
                }
                
                list.add( feed );
            }
        }

        return list;
    }
    
    
}

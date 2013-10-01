/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the
 *  terms of version 2.1 of the GNU Lesser General Public License as published by
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.rest.panels.request;

import com.eviware.soapui.impl.rest.RestMethod;
import com.eviware.soapui.impl.rest.RestRequestInterface;
import com.eviware.soapui.impl.rest.RestResource;
import com.eviware.soapui.impl.rest.actions.request.AddRestRequestToTestCaseAction;
import com.eviware.soapui.impl.rest.support.RestParamProperty;
import com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder;
import com.eviware.soapui.impl.rest.support.RestUtils;
import com.eviware.soapui.impl.rest.support.XmlBeansRestParamsTestPropertyHolder;
import com.eviware.soapui.impl.support.actions.ShowOnlineHelpAction;
import com.eviware.soapui.impl.support.panels.AbstractHttpXmlRequestDesktopPanel;
import com.eviware.soapui.impl.wsdl.WsdlSubmitContext;
import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestInterface;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.Request.SubmitException;
import com.eviware.soapui.model.iface.Submit;
import com.eviware.soapui.model.support.AbstractModelItem;
import com.eviware.soapui.model.support.TestPropertyListenerAdapter;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.swing.SwingActionDelegate;
import com.eviware.soapui.support.components.JXToolBar;
import org.apache.xmlbeans.impl.values.XmlValueDisconnectedException;

import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static com.eviware.soapui.impl.rest.RestRequestInterface.RequestMethod;
import static com.eviware.soapui.impl.rest.actions.support.NewRestResourceActionBase.ParamLocation;
import static com.eviware.soapui.impl.rest.support.RestParamsPropertyHolder.ParameterStyle;

public abstract class AbstractRestRequestDesktopPanel<T extends ModelItem, T2 extends RestRequestInterface> extends
		AbstractHttpXmlRequestDesktopPanel<T, T2>
{
	private static final int STANDARD_TOOLBAR_HEIGHT = 45;
	// package protected to facilitate unit testing
	TextPanelWithTopLabel resourcePanel;
	private TextPanelWithTopLabel queryPanel;
	private JLabel pathLabel;
	private InternalTestPropertyListener testPropertyListener = new InternalTestPropertyListener();
	private RestParamPropertyChangeListener restParamPropertyChangeListener = new RestParamPropertyChangeListener();

	public AbstractRestRequestDesktopPanel( T modelItem, T2 requestItem )
	{
		super( modelItem, requestItem );

		addPropertyChangeListenerToResource( requestItem );

		requestItem.addTestPropertyListener( testPropertyListener );
		requestItem.getOperation().getInterface().addPropertyChangeListener( new EndpointChangeListener() );

		for( TestProperty param : requestItem.getParams().getProperties().values() )
		{
			( ( RestParamProperty )param ).addPropertyChangeListener( restParamPropertyChangeListener );
		}
		requestItem.getResource().addPropertyChangeListener( RestResource.PATH_PROPERTY, new ResourceChangeListener() );
	}

	private void addPropertyChangeListenerToResource( T2 requestItem )
	{
		if( requestItem.getResource() != null )
		{
			requestItem.getResource().addPropertyChangeListener( this );
			requestItem.getResource().addTestPropertyListener( testPropertyListener );
		}
	}

	public void propertyChange( PropertyChangeEvent evt )
	{
		updateFullPathLabel();

		if( ( evt.getPropertyName().equals( "path" ) || evt.getPropertyName().equals( "restMethod" ) )
				&& ( getRequest().getResource() == null || getRequest().getResource() == evt.getSource() ) )
		{
			if( pathLabel != null )
			{
				updateFullPathLabel();
			}
		}

		super.propertyChange( evt );
	}


	@Override
	protected Submit doSubmit() throws SubmitException
	{
		return getRequest().submit( new WsdlSubmitContext( getModelItem() ), true );
	}

	@Override
	protected String getHelpUrl()
	{
		return null;
	}

	@Override
	protected void insertButtons( JXToolBar toolbar )
	{
		if( getRequest().getResource() == null )
		{
			JButton addToTestCaseButton = createActionButton( SwingActionDelegate.createDelegate(
					AddRestRequestToTestCaseAction.SOAPUI_ACTION_ID, getRequest(), null, "/addToTestCase.gif" ), true );
			toolbar.add( addToTestCaseButton );
		}
	}

	@Override
	protected JComponent buildToolbar()
	{
		if( getRequest().getResource() != null )
		{
			JPanel panel = new JPanel( new BorderLayout() );

			JXToolBar baseToolBar = UISupport.createToolbar();

			JComponent submitButton = super.getSubmitButton();
			baseToolBar.add( submitButton );
			baseToolBar.add( cancelButton );

			// insertButtons injects different buttons for different editors. It is overridden in other subclasses
			insertButtons( baseToolBar );
			JPanel methodPanel = new JPanel( new BorderLayout() );
			JComboBox<RequestMethod> methodComboBox = new JComboBox<RequestMethod>( new RestRequestMethodModel( getRequest() ) );
			methodComboBox.setSelectedItem( getRequest().getMethod() );

			JLabel methodLabel = new JLabel( "Method" );
			methodPanel.add( methodLabel, BorderLayout.NORTH );
			methodPanel.add( methodComboBox, BorderLayout.SOUTH );
			methodPanel.setMinimumSize( new Dimension( 75, STANDARD_TOOLBAR_HEIGHT ) );
			//TODO: remove hard coded height adjustment
			methodPanel.setMaximumSize( new Dimension( 75, STANDARD_TOOLBAR_HEIGHT + 10 ) );

			JPanel endpointPanel = new JPanel( new BorderLayout() );
			endpointPanel.setMinimumSize( new Dimension( 75, STANDARD_TOOLBAR_HEIGHT ) );

			JComponent endpointCombo = buildEndpointComponent();
			super.setEndpointComponent( endpointCombo );

			JLabel endPointLabel = new JLabel( "Endpoint" );

			endpointPanel.add( endPointLabel, BorderLayout.NORTH );
			endpointPanel.add( endpointCombo, BorderLayout.SOUTH );


			baseToolBar.addWithOnlyMinimumHeight( methodPanel );
			baseToolBar.add( Box.createHorizontalStrut( 4 ) );
			baseToolBar.addWithOnlyMinimumHeight( endpointPanel );
			baseToolBar.add( Box.createHorizontalStrut( 4 ) );

			addResourceAndQueryField( baseToolBar );

			baseToolBar.add( Box.createHorizontalGlue() );
			baseToolBar.add( tabsButton );
			baseToolBar.add( splitButton );
			baseToolBar.add( UISupport.createToolbarButton( new ShowOnlineHelpAction( getHelpUrl() ) ) );
			int maximumPreferredHeight = findMaximumPreferredHeight( baseToolBar ) + 6;
			baseToolBar.setPreferredSize( new Dimension( 600, Math.max( maximumPreferredHeight, STANDARD_TOOLBAR_HEIGHT ) ) );

			panel.add( baseToolBar, BorderLayout.NORTH );

			addMethodSelectorToolbar( panel );

			return panel;
		}
		else
		{
			//TODO: If we don't need special clause for empty resources then remove it
			return super.buildToolbar();
		}
	}

	private int findMaximumPreferredHeight( Container parent )
	{
		int maximum = 0;
		for( Component component : parent.getComponents() )
		{
			int componentPreferredHeight = component == null || component.getPreferredSize() == null ? 0 : component.getPreferredSize().height;
			maximum = Math.max( maximum, componentPreferredHeight );
		}
		return maximum;
	}

	private void addResourceAndQueryField( JXToolBar toolbar )
	{
		if( !( getRequest() instanceof RestTestRequestInterface ) )
		{
			String path = getRequest().getResource().getPath();
			resourcePanel = new TextPanelWithTopLabel( "Resource", path );
			toolbar.addWithOnlyMinimumHeight( resourcePanel );

			toolbar.add( Box.createHorizontalStrut( 4 ) );

			String query = RestUtils.getQueryParamsString( getRequest().getParams(), getRequest() );
			queryPanel = new TextPanelWithTopLabel( "Query", query, false );
			toolbar.addWithOnlyMinimumHeight( queryPanel );
		}
	}

	private void addMethodSelectorToolbar( JPanel panel )
	{
		if( getRequest().getResource() != null && getRequest() instanceof RestTestRequestInterface )
		{
			JXToolBar toolbar = UISupport.createToolbar();
			JComboBox pathCombo = new JComboBox( new PathComboBoxModel() );
			pathCombo.setRenderer( new RestMethodListCellRenderer() );
			pathCombo.setPreferredSize( new Dimension( 200, 20 ) );
			pathCombo.setSelectedItem( getRequest().getRestMethod() );

			toolbar.addLabeledFixed( "Resource/Method:", pathCombo );
			toolbar.addSeparator();

			pathLabel = new JLabel();
			updateFullPathLabel();

			toolbar.add( pathLabel );
			panel.add( toolbar, BorderLayout.SOUTH );
		}
	}

	protected boolean release()
	{
		if( getRequest().getResource() != null )
		{
			getRequest().getResource().removePropertyChangeListener( this );
		}

		getRequest().removeTestPropertyListener( testPropertyListener );

		for( TestProperty param : getRequest().getParams().getProperties().values() )
		{
			( ( RestParamProperty )param ).removePropertyChangeListener( restParamPropertyChangeListener );
		}

		return super.release();
	}

	private class InternalTestPropertyListener extends TestPropertyListenerAdapter
	{
		@Override
		public void propertyValueChanged( String name, String oldValue, String newValue )
		{
			updateGuiValues();
		}

		@Override
		public void propertyAdded( String name )
		{
			updateGuiValues();
			RestParamProperty property = getRequest().getParams().getProperty( name );
			property.addPropertyChangeListener( restParamPropertyChangeListener );
		}

		@Override
		public void propertyRemoved( String name )
		{
			updateGuiValues();
		}

		@Override
		public void propertyRenamed( String oldName, String newName )
		{
			updateGuiValues();
		}
	}


	protected void updateGuiValues()
	{
		resetQueryPanelText();
		rebuildResourcePanelText();
		updateFullPathLabel();

	}

	protected void resetQueryPanelText()
	{
		if( queryPanel != null )
		{
			queryPanel.setText( RestUtils.getQueryParamsString( getRequest().getParams(), getRequest() ) );
		}
	}

	protected void rebuildResourcePanelText()
	{
		if( resourcePanel != null )
		{
			resourcePanel.setText( getRequest().getResource().getFullPath() );
		}
	}

	protected void updateFullPathLabel()
	{
		if( pathLabel != null && getRequest().getResource() != null )
		{
			String text = RestUtils.expandPath( getRequest().getResource().getFullPath(), getRequest().getParams(),
					getRequest() );
			pathLabel.setText( "[" + text + "]" );
			pathLabel.setToolTipText( text );
		}
	}


	private class RestParamPropertyChangeListener implements PropertyChangeListener
	{
		public void propertyChange( PropertyChangeEvent evt )
		{
			try
			{
				if( evt.getPropertyName().equals( XmlBeansRestParamsTestPropertyHolder.PROPERTY_STYLE ) )
				{
					RestParamProperty source = ( RestParamProperty )evt.getSource();
					( ( AbstractModelItem )source.getModelItem() ).notifyPropertyChanged( evt.getPropertyName(),
							evt.getOldValue(), evt.getNewValue() );
				}

				if( evt.getPropertyName().equals( XmlBeansRestParamsTestPropertyHolder.PARAM_LOCATION ) )
				{
					RestParamProperty source = ( RestParamProperty )evt.getSource();
					String propName = source.getName();
					String propValue = source.getValue();
					ParameterStyle propStyle = source.getStyle();
					String requestLevelValue = getRequest().getParams().getProperty( propName ).getValue();
					removePropertyFromLevel( source.getName(), ( ParamLocation )evt.getOldValue() );
					addPropertyToLevel( propName, propValue, propStyle, ( ParamLocation )evt.getNewValue(), requestLevelValue );
				}
			}
			catch( XmlValueDisconnectedException exception )
			{
				//Do nothing, it must have been removed by another request editor instance under the same resource/method
			}
			updateGuiValues();
		}


	}

	private void addPropertyToLevel( String name, String value, ParameterStyle style, ParamLocation location,
												String requestLevelValue )
	{
		RestParamsPropertyHolder paramsPropertyHolder = null;
		switch( location )
		{
			case METHOD:
				paramsPropertyHolder = getRequest().getRestMethod().getParams();
				break;
			case RESOURCE:
				paramsPropertyHolder = getRequest().getResource().getParams();
				break;
			//case REQUEST:     TODO: uncomment when we support request level parameters
			//	paramsPropertyHolder = getRequest().getParams();
			//	break;
		}

		if( paramsPropertyHolder != null )
		{
			paramsPropertyHolder.addProperty( name );
			RestParamProperty addedParameter = paramsPropertyHolder.getProperty( name );
			addedParameter.addPropertyChangeListener( restParamPropertyChangeListener );
			addedParameter.setValue( value );
			addedParameter.setDefaultValue( value );
			addedParameter.setStyle( style );
			//Override the request level value as well
			getRequest().getParams().getProperty( name ).setValue( requestLevelValue );
		}
		addPropertyChangeListenerToResource( getRequest() );
	}

	private void removePropertyFromLevel( String propertyName, ParamLocation location )
	{
		switch( location )
		{
			case METHOD:
				getRequest().getRestMethod().removeProperty( propertyName );
				break;
			case RESOURCE:
				getRequest().getResource().removeProperty( propertyName );
				break;
		}

	}




	private class PathComboBoxModel extends AbstractListModel implements ComboBoxModel
	{
		public int getSize()
		{
			int sz = 0;
			for( RestResource resource : getRequest().getResource().getService().getAllResources() )
			{
				sz += resource.getRestMethodCount();
			}

			return sz;
		}

		public Object getElementAt( int index )
		{
			int sz = 0;
			for( RestResource resource : getRequest().getResource().getService().getAllResources() )
			{
				if( index < sz + resource.getRestMethodCount() )
				{
					return resource.getRestMethodAt( index - sz );
				}

				sz += resource.getRestMethodCount();
			}

			return null;
		}

		public void setSelectedItem( Object anItem )
		{
			( ( RestTestRequestInterface )getRequest() ).getTestStep().setRestMethod( ( RestMethod )anItem );
		}

		public Object getSelectedItem()
		{
			return getRequest().getRestMethod();
		}
	}

	private class RestMethodListCellRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent( JList list, Object value, int index, boolean isSelected,
																	  boolean cellHasFocus )
		{
			Component result = super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );

			if( value instanceof RestMethod )
			{
				RestMethod item = ( RestMethod )value;
				setIcon( item.getIcon() );
				setText( item.getResource().getName() + " -> " + item.getName() );
			}

			return result;
		}

	}


	class TextPanelWithTopLabel extends JPanel
	{
		private final Color MAC_DISABLED_BGCOLOR = new Color( 232, 232, 232 );

		JLabel textLabel;
		JTextField textField;

		TextPanelWithTopLabel( String label, String text )
		{
			textLabel = new JLabel( label );
			textField = new JTextField( text );
			setToolTipText( text );
			super.setLayout( new BorderLayout() );
			super.add( textLabel, BorderLayout.NORTH );
			super.add( textField, BorderLayout.SOUTH );
		}

		public TextPanelWithTopLabel( String label, String text, boolean isEditable )
		{
			this( label, text );
			textField.setEditable( isEditable );
			if( !isEditable && UISupport.isMac() )
			{
				textField.setBackground( MAC_DISABLED_BGCOLOR );
			}
		}

		public TextPanelWithTopLabel( String label, String text, DocumentListener documentListener )
		{
			this( label, text );
			textField.getDocument().addDocumentListener( documentListener );
		}


		public String getText()
		{
			return textField.getText();
		}

		public void setText( String text )
		{
			textField.setText( text );
			setToolTipText( text );
		}

		@Override
		public void setToolTipText( String text )
		{
			super.setToolTipText( text );
			textLabel.setToolTipText( text );
			textField.setToolTipText( text );
		}
	}

	//TODO: Temporary fix resource panel should be moved to appropriate subclass
	private String getResourcePanelText()
	{
		if( resourcePanel == null )
			return "";
		else
			return resourcePanel.getText();
	}

	private void setResourcePanelText( String text )
	{
		if( resourcePanel != null )
		{
			resourcePanel.setText( text );
		}
	}

	private class EndpointChangeListener implements PropertyChangeListener
	{


		@Override
		public void propertyChange( PropertyChangeEvent evt )
		{
			if( evt.getPropertyName().equals( Interface.ENDPOINT_PROPERTY ) )
			{
				Object currentEndpoint = endpointsModel.getSelectedItem();
				if( currentEndpoint != null && currentEndpoint.equals( evt.getOldValue() ) )
				{
					endpointsModel.setSelectedItem( evt.getNewValue() );
				}
			}
		}
	}

	private class ResourceChangeListener implements PropertyChangeListener {

		@Override
		public void propertyChange( PropertyChangeEvent evt )
		{
			rebuildResourcePanelText();
		}
	}


}

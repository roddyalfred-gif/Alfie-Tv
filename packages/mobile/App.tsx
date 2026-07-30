import React, { useMemo, useState } from 'react';
import { FlatList, SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { buildChannelSummary } from './src/channelViewModel';
import { getMobileChannels } from './src/channelStore';
import { createNavigationState, navigateTo } from '@alfie-tv/core';

export default function App() {
  const channels = getMobileChannels();
  const [navigation, setNavigation] = useState(() => createNavigationState('home'));
  const screenLabel = useMemo(() => navigation.currentScreen.toUpperCase(), [navigation.currentScreen]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Alfie TV</Text>
        <Text style={styles.subtitle}>Watch your favorite live channels anywhere</Text>
        <View style={styles.navRow}>
          <TouchableOpacity onPress={() => setNavigation((current) => navigateTo(current, 'home'))} style={styles.navButton}>
            <Text style={styles.navText}>Home</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setNavigation((current) => navigateTo(current, 'channels'))} style={styles.navButton}>
            <Text style={styles.navText}>Channels</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setNavigation((current) => navigateTo(current, 'player'))} style={styles.navButton}>
            <Text style={styles.navText}>Player</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setNavigation((current) => navigateTo(current, 'settings'))} style={styles.navButton}>
            <Text style={styles.navText}>Settings</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.statusBanner}>
          <Text style={styles.screenBadge}>{screenLabel}</Text>
          <Text style={styles.statusText}>Shared client state active</Text>
        </View>
      </View>

      <View style={styles.quickActions}>
        <Text style={styles.quickTitle}>Quick actions</Text>
        <View style={styles.quickRow}>
          <View style={styles.quickChip}><Text style={styles.quickChipText}>Favorites</Text></View>
          <View style={styles.quickChip}><Text style={styles.quickChipText}>Resume</Text></View>
          <View style={styles.quickChip}><Text style={styles.quickChipText}>Watch later</Text></View>
        </View>
      </View>

      <FlatList
        data={channels}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        renderItem={({ item }) => {
          const summary = buildChannelSummary({
            id: item.id,
            name: item.name,
            category: item.category,
            quality: item.quality,
            isFavorite: item.isFavorite,
          });

          return (
            <View style={styles.card}>
              <View style={styles.cardHeader}>
                <Text style={styles.channelName}>{summary.title}</Text>
                <Text style={styles.badge}>{summary.status}</Text>
              </View>
              <Text style={styles.meta}>{summary.subtitle}</Text>
            </View>
          );
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    justifyContent: 'center',
    padding: 24,
  },
  header: {
    marginBottom: 16,
  },
  title: {
    color: '#f8fafc',
    fontSize: 28,
    fontWeight: '700',
    marginBottom: 8,
  },
  subtitle: {
    color: '#e2e8f0',
    fontSize: 16,
  },
  navRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 12,
    marginBottom: 8,
  },
  navButton: {
    backgroundColor: '#1d4ed8',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
  },
  navText: {
    color: '#eff6ff',
    fontSize: 12,
    fontWeight: '600',
  },
  statusBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#1e293b',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  screenBadge: {
    color: '#fcd34d',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  statusText: {
    color: '#cbd5e1',
    fontSize: 12,
  },
  quickActions: {
    marginBottom: 12,
    backgroundColor: '#111827',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#334155',
    padding: 12,
  },
  quickTitle: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 8,
    textTransform: 'uppercase',
  },
  quickRow: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  quickChip: {
    backgroundColor: '#1d4ed8',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  quickChipText: {
    color: '#eff6ff',
    fontSize: 12,
  },
  list: {
    gap: 12,
  },
  card: {
    backgroundColor: '#111827',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  channelName: {
    color: '#f8fafc',
    fontSize: 16,
    fontWeight: '600',
  },
  meta: {
    color: '#94a3b8',
    fontSize: 14,
  },
  badge: {
    color: '#fcd34d',
    fontWeight: '600',
  },
});
